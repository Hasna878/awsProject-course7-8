package com.iot.project;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;

public class ExportClient {

    // À adapter sur AWS (région du bucket)
    private static final Region REGION = Region.EU_WEST_3;

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java ExportClient <bucket> <key> <srcIp> <dstIp>");
            System.exit(1);
        }

        String bucket = args[0];
        String key = args[1];
        String srcIpFilter = args[2];
        String dstIpFilter = args[3];

        try {
            export(bucket, key, srcIpFilter, dstIpFilter);
            System.out.println("✔ Export terminé !");
        } catch (Exception e) { // IOException + CsvValidationException
            System.err.println("Erreur export : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void export(String bucket, String key,
                              String srcFilter, String dstFilter)
            throws IOException, CsvValidationException {

        S3Client s3 = S3Client.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        // 1. Télécharger consolidated.csv depuis S3 dans un fichier temporaire
        Path tempFile = Files.createTempFile("consolidated-", ".csv");

        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.getObject(req, ResponseTransformer.toFile(tempFile));

        // 2. Lire le fichier et filtrer
        try (CSVReader reader = new CSVReader(
                new FileReader(tempFile.toFile(), StandardCharsets.UTF_8));
             CSVWriter writer = new CSVWriter(
                     new FileWriter("export.csv", StandardCharsets.UTF_8))) {

            String[] header = reader.readNext(); // peut lancer CsvValidationException
            if (header == null) {
                throw new IOException("consolidated.csv vide !");
            }

            writer.writeNext(header);

            String[] line;
            while ((line = reader.readNext()) != null) { // idem
                String srcIp = line[0];
                String dstIp = line[1];

                if (srcIp.equals(srcFilter) && dstIp.equals(dstFilter)) {
                    writer.writeNext(line);
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }

        System.out.println("✔ Fichier export.csv généré (filtré).");
    }
}
