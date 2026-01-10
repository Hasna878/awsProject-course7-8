package com.iot.project;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SummarizeWorker {

    // Structure pour garder les sommes
    static class Aggregate {
        long totalFlowDuration;
        long totalFwdPkt;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java SummarizeWorker <inputCsvPath> <outputCsvPath>");
            System.exit(1);
        }

        String inputCsv = args[0];   // fichier brut (VARIoT)
        String outputCsv = args[1];  // fichier résumé à produire

        try {
            summarize(inputCsv, outputCsv);
            System.out.println("✔ Résumé généré dans : " + outputCsv);
        } catch (Exception e) {
            System.err.println("Erreur pendant le résumé : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void summarize(String inputCsv, String outputCsv) throws IOException, CsvValidationException {
        // Map clé -> agrégat
        Map<String, Aggregate> aggregates = new HashMap<>();

        try (CSVReader reader = new CSVReader(
                new FileReader(inputCsv, StandardCharsets.UTF_8))) {

            // ---- 1) Lire l’entête ----
            String[] header = reader.readNext(); // peut lancer CsvValidationException
            if (header == null) {
                throw new IOException("CSV vide : " + inputCsv);
            }

            // Trouver les index des colonnes qui nous intéressent
            int idxTimestamp = findIndex(header, "Timestamp");
            int idxSrcIp = findIndex(header, "Src IP");
            int idxDstIp = findIndex(header, "Dst IP");
            int idxFlowDur = findIndex(header, "Flow Duration");
            int idxTotFwdPkt = findIndex(header, "Tot Fwd Pkts");

            if (idxTimestamp == -1 || idxSrcIp == -1 || idxDstIp == -1
                    || idxFlowDur == -1 || idxTotFwdPkt == -1) {
                throw new IOException("Certaines colonnes nécessaires sont introuvables dans le CSV");
            }

            // ---- 2) Lire toutes les lignes ----
            String[] line;
            long lineCount = 0;

            while ((line = reader.readNext()) != null) { // readNext peut aussi lancer CsvValidationException
                lineCount++;

                String timestamp = line[idxTimestamp];
                String srcIp = line[idxSrcIp];
                String dstIp = line[idxDstIp];
                String flowDurStr = line[idxFlowDur];
                String totFwdPktStr = line[idxTotFwdPkt];

                // Extraire la date (partie avant l'espace)
                String date = extractDate(timestamp);

                long flowDur = parseLongSafe(flowDurStr);
                long totFwdPkt = parseLongSafe(totFwdPktStr);

                // Clé : Date|SrcIP|DstIP
                String key = date + "|" + srcIp + "|" + dstIp;

                Aggregate agg = aggregates.get(key);
                if (agg == null) {
                    agg = new Aggregate();
                    aggregates.put(key, agg);
                }

                agg.totalFlowDuration += flowDur;
                agg.totalFwdPkt += totFwdPkt;
            }

            System.out.println("Nombre de lignes lues : " + lineCount);
            System.out.println("Nombre de groupes (Date,SrcIP,DstIP) : " + aggregates.size());
        }

        // ---- 3) Écrire le fichier résumé ----
        try (CSVWriter writer = new CSVWriter(
                new FileWriter(outputCsv, StandardCharsets.UTF_8))) {
            // Header
            String[] outHeader = {
                "Date", "SrcIP", "DstIP",
                "TotalFlowDuration", "TotalFwdPkt"
            };
            writer.writeNext(outHeader);

            // Contenu
            for (Map.Entry<String, Aggregate> entry : aggregates.entrySet()) {
                String key = entry.getKey();
                Aggregate agg = entry.getValue();

                String[] parts = key.split("\\|", 3);
                String date = parts[0];
                String srcIp = parts[1];
                String dstIp = parts[2];

                String[] row = {
                    date,
                    srcIp,
                    dstIp,
                    String.valueOf(agg.totalFlowDuration),
                    String.valueOf(agg.totalFwdPkt)
                };
                writer.writeNext(row);
            }
        }
    }

    // Trouve l'index d'une colonne par son nom
    private static int findIndex(String[] header, String colName) {
        for (int i = 0; i < header.length; i++) {
            if (colName.equals(header[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    // Extrait juste la date du timestamp
    // Exemple : "2022-12-07 10:15:30" -> "2022-12-07"
    private static String extractDate(String timestamp) {
        if (timestamp == null) {
            return "";
        }
        String[] parts = timestamp.split(" ");
        return parts[0]; // première partie avant l'espace
    }

    // Convertit en long en gérant les valeurs vides / invalides
    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
