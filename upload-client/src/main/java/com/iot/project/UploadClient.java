package com.iot.project;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.file.Paths;

public class UploadClient {

    private final S3Client s3;
    private final SqsClient sqs;

    public UploadClient() {
        this.s3 = S3Client.builder()
                .region(Region.EU_WEST_3)   // Change selon la région (Paris)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.sqs = SqsClient.builder()
                .region(Region.EU_WEST_3)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public void uploadFileToS3(String bucketName, String key, String filePath) {
        System.out.println("Uploading file to S3...");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.putObject(request, Paths.get(filePath));
        System.out.println("✔ File uploaded to S3: " + bucketName + "/" + key);
    }

    public void sendMessageToSqs(String queueUrl, String messageBody) {
        System.out.println("Sending message to SQS...");

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqs.sendMessage(request);
        System.out.println("SQS message sent!");
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java UploadClient <localFilePath> <bucketName> <queueUrl>");
            System.exit(1);
        }

        String filePath = args[0];
        String bucket = args[1];
        String queueUrl = args[2];

        String key = "raw/" + Paths.get(filePath).getFileName().toString();

        UploadClient client = new UploadClient();

        // Upload du fichier IoT vers S3
        client.uploadFileToS3(bucket, key, filePath);

        // Envoi du message SQS pour déclencher Summarize Worker
        String message = "{ \"bucket\": \"" + bucket + "\", \"key\": \"" + key + "\" }";
        client.sendMessageToSqs(queueUrl, message);

        System.out.println("Upload Client terminé!");
    }
}
