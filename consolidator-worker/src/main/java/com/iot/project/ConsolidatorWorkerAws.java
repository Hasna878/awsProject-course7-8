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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ConsolidatorWorkerAws {

    //  À remplacer PLUS TARD 
    private static final Region REGION = Region.EU_WEST_3;
    private static final String CONSOLIDATE_QUEUE_URL =
            "REPLACE_ME_CONSOLIDATE_QUEUE_URL";

    // exemple arn:aws:sns:eu-west-3:123456789012:iot-alerts-topic
    private static final String ALERT_TOPIC_ARN =
            "REPLACE_ME_SNS_TOPIC_ARN";


    private final S3Client s3;
    private final SqsClient sqs;
    private final SnsClient sns;

    public ConsolidatorWorkerAws() {
        this.s3 = S3Client.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.sqs = SqsClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.sns = SnsClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }


    public static void main(String[] args) {
        ConsolidatorWorkerAws worker = new ConsolidatorWorkerAws();
        System.out.println("ConsolidatorWorker AWS démarré, écoute consolidate-queue...");

        while (true) {
            try {
                worker.pollOnce();
            } catch (Exception e) {
                e.printStackTrace();
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void pollOnce() throws IOException, CsvValidationException {

        ReceiveMessageRequest req = ReceiveMessageRequest.builder()
                .queueUrl(CONSOLIDATE_QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20)
                .build();

        List<Message> messages = sqs.receiveMessage(req).messages();
        if (messages.isEmpty()) return;

        for (Message m : messages) {
            String body = m.body();
            System.out.println("Message reçu : " + body);

            String bucket = extractJson(body, "bucket");
            String key = extractJson(body, "key");

            if (bucket == null || key == null) {
                System.err.println("Message invalide, suppression : " + body);
                deleteMessage(m);
                continue;
            }

            process(bucket, key, m);
        }
    }

    private void process(String bucket, String summaryKey, Message msg)
            throws IOException, CsvValidationException {

        System.out.println("Traitement résumé S3 : " + bucket + "/" + summaryKey);

        Path tempSummary = Files.createTempFile("summary-", ".csv");
        Path tempConsolidated = Files.createTempFile("consolidated-", ".csv");

        try {
            // 1 - Télécharger le résumé depuis S3
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(summaryKey)
                    .build();
            s3.getObject(getReq, ResponseTransformer.toFile(tempSummary));

            // 2 - Télécharger l'ancien consolidated.csv (s'il existe)
            Path finalConsolidated = tempConsolidated;
            String consolidatedKey = "consolidated/consolidated.csv";

            try {
                GetObjectRequest getOld = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(consolidatedKey)
                        .build();
                s3.getObject(getOld, ResponseTransformer.toFile(tempConsolidated));
            } catch (Exception e) {
                System.out.println("Pas de consolidated.csv existant. Création d'un nouveau.");
            }

            // 3 - Lancer la consolidation locale
            System.out.println("Consolidation en cours...");
            ConsolidatorWorker.consolidate(
                    tempSummary.toString(),
                    finalConsolidated.toString()
            );

            // 4 - Upload du fichier consolidé vers S3
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(consolidatedKey)
                    .build();
            s3.putObject(putReq, finalConsolidated);
            System.out.println("consolidated.csv uploadé : " + bucket + "/" + consolidatedKey);

            // 4bis - Publier une alerte SNS
            if (ALERT_TOPIC_ARN != null && !ALERT_TOPIC_ARN.startsWith("REPLACE_ME")) {
                String message = "Nouveau fichier consolidé généré pour le bucket " + bucket +
                        " et le résumé " + summaryKey;

                PublishRequest pubReq = PublishRequest.builder()
                        .topicArn(ALERT_TOPIC_ARN)
                        .message(message)
                        .subject("IoT Consolidation Done")
                        .build();

                sns.publish(pubReq);
                System.out.println("Notification SNS envoyée sur le topic : " + ALERT_TOPIC_ARN);
            } else {
                System.out.println("ALERT_TOPIC_ARN non configuré, SNS non utilisé.");
            }

            // 5 - Supprimer message SQS
            deleteMessage(msg);
            System.out.println("Résumé consolidé → message SQS supprimé.");

        } finally {
            Files.deleteIfExists(tempSummary);
            Files.deleteIfExists(tempConsolidated);
        }
    }

    private void deleteMessage(Message m) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(CONSOLIDATE_QUEUE_URL)
                .receiptHandle(m.receiptHandle())
                .build());
    }

    private static String extractJson(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colon = json.indexOf(":", idx);
        int firstQuote = json.indexOf("\"", colon);
        int secondQuote = json.indexOf("\"", firstQuote + 1);

        if (firstQuote == -1 || secondQuote == -1) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }
}
