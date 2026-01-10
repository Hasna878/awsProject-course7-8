package com.iot.project;

import com.opencsv.exceptions.CsvValidationException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SummarizeWorkerAws {

    // À ADAPTER AVEC LES VRAIES VALEURS
    private static final Region REGION = Region.EU_WEST_3; // Paris
    private static final String SUMMARIZE_QUEUE_URL =
            "https://sqs.eu-west-3.amazonaws.com/123456789012/summarize-queue";
    private static final String CONSOLIDATE_QUEUE_URL =
            "https://sqs.eu-west-3.amazonaws.com/123456789012/consolidate-queue";

    private final S3Client s3;
    private final SqsClient sqs;

    public SummarizeWorkerAws() {
        this.s3 = S3Client.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.sqs = SqsClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public static void main(String[] args) {
        SummarizeWorkerAws worker = new SummarizeWorkerAws();
        System.out.println("SummarizeWorker AWS démarré, écoute SQS...");

        while (true) {
            try {
                worker.pollOnce();
            } catch (Exception e) {
                System.err.println("Erreur lors du poll SQS : " + e.getMessage());
                e.printStackTrace();
                // petite pause pour éviter de spammer en cas d'erreur répétée
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void pollOnce() throws IOException, CsvValidationException {
        ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                .queueUrl(SUMMARIZE_QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20) // long polling
                .build();

        List<Message> messages = sqs.receiveMessage(req).messages();

        if (messages.isEmpty()) {
            // Pas de message pour l'instant
            return;
        }

        for (Message m : messages) {
            String body = m.body();
            System.out.println("Message reçu : " + body);

            // On suppose que l'UploadClient envoie un JSON simple :
            // { "bucket": "iot-traffic-aymane", "key": "raw/xxx.csv" }
            String bucket = extractJsonValue(body, "bucket");
            String key    = extractJsonValue(body, "key");

            if (bucket == null || key == null) {
                System.err.println("Message invalide, pas de bucket ou key : " + body);
                // on peut supprimer ou laisser retenter, au choix
                deleteMessage(m);
                continue;
            }

            processOneFile(bucket, key, m);
        }
    }

    private void processOneFile(String bucket, String rawKey, Message originalMessage)
            throws IOException, CsvValidationException {

        System.out.println("Traitement du fichier S3 : " + bucket + "/" + rawKey);

        // 1) Télécharger le fichier brut dans un fichier temporaire local
        Path tempInput = Files.createTempFile("raw-", ".csv");
        Path tempOutput = Files.createTempFile("summary-", ".csv");

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(rawKey)
                    .build();

            s3.getObject(getReq, ResponseTransformer.toFile(tempInput));
            System.out.println("Fichier brut téléchargé : " + tempInput);

            // 2) Appeler ta logique locale de résumé
            SummarizeWorker.summarize(tempInput.toString(), tempOutput.toString());
            System.out.println("Résumé local généré : " + tempOutput);

            // 3) Déterminer la clé de sortie pour le résumé
            Path rawPath = Paths.get(rawKey);
            Path rawFileName = rawPath.getFileName();
            if (rawFileName == null) {
                throw new IllegalArgumentException(
                        "Raw key must include a file name."
                );
            }
            String fileName = rawFileName.toString(); // ex: data-20221207.csv
            String summaryFileName = fileName.replace(".csv", "-summary.csv");
            String summaryKey = "summaries/" + summaryFileName;

            // 4) Uploader le résumé vers S3
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(summaryKey)
                    .build();

            s3.putObject(putReq, tempOutput);
            System.out.println("Résumé uploadé vers S3 : " + bucket + "/" + summaryKey);

            // 5) Envoyer un message à la queue de consolidation
            String nextMessageBody = "{ \"bucket\": \"" + bucket + "\", \"key\": \"" + summaryKey + "\" }";

            SendMessageRequest sendReq = SendMessageRequest.builder()
                    .queueUrl(CONSOLIDATE_QUEUE_URL)
                    .messageBody(nextMessageBody)
                    .build();

            sqs.sendMessage(sendReq);
            System.out.println("Message envoyé à consolidate-queue : " + nextMessageBody);

            // 6) Supprimer le message original de la queue summarize-queue
            deleteMessage(originalMessage);
            System.out.println("Message SQS d'origine supprimé (traitement OK).");

        } finally {
            // Nettoyer les fichiers temporaires
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
        }
    }

    private void deleteMessage(Message m) {
        DeleteMessageRequest delReq = DeleteMessageRequest.builder()
                .queueUrl(SUMMARIZE_QUEUE_URL)
                .receiptHandle(m.receiptHandle())
                .build();
        sqs.deleteMessage(delReq);
    }

    // Petit parseur JSON ultra simple pour notre format fixe
    // Cherche "fieldName": "value"
    private static String extractJsonValue(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            return null;
        }
        int colon = json.indexOf(":", idx);
        if (colon == -1) {
            return null;
        }
        int firstQuote = json.indexOf("\"", colon);
        if (firstQuote == -1) {
            return null;
        }
        int secondQuote = json.indexOf("\"", firstQuote + 1);
        if (secondQuote == -1) {
            return null;
        }
        return json.substring(firstQuote + 1, secondQuote);
    }
}
