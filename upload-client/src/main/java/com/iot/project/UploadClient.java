package com.iot.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

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
 * @author EMSE
 * @since 1.0
 */
public class UploadClient {

    /**
     * Logger for application events.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(UploadClient.class);

    /**
     * AWS region for service clients.
     */
    private static final Region AWS_REGION = Region.EU_WEST_3;

    /**
     * Expected number of CLI arguments.
     */
    private static final int EXPECTED_ARGS_COUNT = 3;

    /**
     * Usage message for CLI invocation.
     */
    private static final String USAGE =
            "Usage: java UploadClient <localFilePath> <bucketName> <queueUrl>";

    /**
     * Amazon S3 client for uploads.
     */
    private final S3Client s3;

    /**
     * Amazon SQS client for notifications.
     */
    private final SqsClient sqs;

    /**
     * Creates an UploadClient configured for AWS Region EU_WEST_3.
     * Credentials are automatically loaded using DefaultCredentialsProvider.
     */
    public UploadClient() {
        this.s3 = S3Client.builder()
                .region(AWS_REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.sqs = SqsClient.builder()
                .region(AWS_REGION)
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
    public void uploadFileToS3(
            final String bucketName,
            final String key,
            final String filePath
    ) {
        System.out.println("Uploading file to S3...");

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.putObject(request, Paths.get(filePath));
        System.out.println("File uploaded to S3: " + bucketName + "/" + key);
    }

    /**
     * Sends a message to an SQS queue.
     *
     * @param queueUrl     the SQS queue URL
     * @param messageBody  the body of the message to send
     */
    public void sendMessageToSqs(
            final String queueUrl,
            final String messageBody
    ) {
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
    public static void main(final String[] args) {
        LOGGER.info("Starting UploadClient application...");
        LOGGER.debug(
                "Program started with {} argument(s): {}",
                args.length,
                Arrays.toString(args)
        );
        if (args.length == 0) {
            LOGGER.warn(
                    "No arguments were provided. Using default configuration."
            );
        }

        String example = "debug test";
        LOGGER.info("Example variable: {}", example);

        try {
            if (args.length != EXPECTED_ARGS_COUNT) {
                System.out.println(USAGE);
                System.exit(1);
            }

            String filePath = args[0];
            String bucket = args[1];
            String queueUrl = args[2];

            Path file = Paths.get(filePath);
            Path fileName = file.getFileName();
            if (fileName == null) {
                throw new IllegalArgumentException(
                        "File path must include a file name."
                );
            }
            String key = "raw/" + fileName.toString();

            UploadClient client = new UploadClient();

            // Upload du fichier IoT vers S3
            client.uploadFileToS3(bucket, key, filePath);

            // Envoi du message SQS pour declencher Summarize Worker
            String message = String.format(
                    "{ \"bucket\": \"%s\", \"key\": \"%s\" }",
                    bucket,
                    key
            );
            client.sendMessageToSqs(queueUrl, message);

            System.out.println("Upload Client finished!");
        } catch (Exception e) {
            LOGGER.error(
                    "Unexpected error during UploadClient execution: {}",
                    e.getMessage(),
                    e
            );
        }
    }

}
