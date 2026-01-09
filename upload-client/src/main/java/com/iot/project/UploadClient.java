package com.iot.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.file.Paths;
/**
 * UploadClient is responsible for uploading IoT files to AWS S3
 * and sending notifications/messages to an AWS SQS queue.
 *
 * <p>This class initializes AWS clients using default credentials and
 * provides high-level methods for:
 * <ul>
 *   <li>Uploading files to an S3 bucket</li>
 *   <li>Sending messages to an SQS queue</li>
 * </ul>
 *
 * <h2>Example usage (CLI):</h2>
 * <pre>{@code
 * java UploadClient <localFilePath> <bucketName> <queueUrl>
 * }</pre>
 *
 * @author 
 * @since 1.0
 */

public class UploadClient {

    private static final Logger logger = LoggerFactory.getLogger(UploadClient.class);


    private final S3Client s3;
    private final SqsClient sqs;
    
    /**
     * Creates an UploadClient configured for AWS Region EU_WEST_3.
     * Credentials are automatically loaded using DefaultCredentialsProvider.
     */

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
   /**
     * Uploads a local file to an S3 bucket.
     *
     * @param bucketName the name of the S3 bucket
     * @param key        the destination key (path) inside the bucket
     * @param filePath   the local file path to upload
     */
    public void uploadFileToS3(String bucketName, String key, String filePath) {
        System.out.println("Uploading file to S3...");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.putObject(request, Paths.get(filePath));
        System.out.println("✔ File uploaded to S3: " + bucketName + "/" + key);
    }
    /**
     * Sends a message to an SQS queue.
     *
     * @param queueUrl     the SQS queue URL
     * @param messageBody  the body of the message to send
     */

    public void sendMessageToSqs(String queueUrl, String messageBody) {
        System.out.println("Sending message to SQS...");

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqs.sendMessage(request);
        System.out.println("SQS message sent!");
    }
     /**
     * CLI entry point for uploading a file and triggering a worker via SQS.
     *
     * Expected arguments:
     * <ol>
     *   <li>localFilePath</li>
     *   <li>bucketName</li>
     *   <li>queueUrl</li>
     * </ol>
     *
     * @param args command-line arguments
     */

    public static void main(String[] args) {
        
        logger.info("Starting UploadClient application...");
        logger.debug("Program started with {} argument(s): {}", args.length, java.util.Arrays.toString(args));
        if (args.length == 0) {
        logger.warn("No arguments were provided to UploadClient. Using default configuration.");
        }

        String example = "debug test";
        logger.info("Example variable: {}", example);


        try{
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
        } catch(Exception e){
            logger.error("Unexpected error during UploadClient execution: {}", e.getMessage(), e);

        }

    }

}
