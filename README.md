# 📘 README — IoT Traffic Summarization & Consolidation on AWS
**Cloud & Edge Infrastructures — 2025**

## 🧱 1. Introduction

Ce projet implémente une solution cloud temporaire permettant de :

* Uploader des fichiers CSV bruts contenant du trafic IoT
* Résumer le trafic par (Date, SrcIP, DstIP)
* Consolider les statistiques globales par combinaison (SrcIP, DstIP)
* Exporter les données consolidées filtrées
* Recevoir une notification SNS lorsque `consolidated.csv` est mis à jour

L’objectif : analyser le trafic IoT, détecter des anomalies et optimiser l’infrastructure de l’entreprise sans déployer un cluster permanent.

## 🏗️ 2. Architecture Globale

```text
                ┌──────────────────────┐
                │    UploadClient      │  (Local / PC)
                │    (Java + AWS SDK)  │
                └──────────┬───────────┘
                           │
                S3 Upload  │  SQS Message
                           ▼
                 ┌──────────────────┐
                 │ S3 Bucket        │
                 │ raw/*.csv        │
                 └─────────┬────────┘
                           │
        Trigger via SQS    │
                           ▼
       ┌──────────────────────────────┐
       │  SummarizeWorkerAws (EC2)    │
       │  - Download raw CSV          │
       │  - Compute daily summary     │
       │  - Upload summary/*.csv      │
       │  - Send message to SQS       │
       └──────────────┬──────────────┘
                      │
                      ▼
           ┌────────────────────────┐
           │  SQS consolidate-queue │
           └────────────┬──────────┘
                        │
                        ▼
 ┌─────────────────────────────────────────┐
 │  ConsolidatorWorkerAws (EC2)            │
 │  - Download summary                     │
 │  - Merge into consolidated.csv          │
 │  - Upload consolidated/consolidated.csv │
 │  - Publish SNS alert                    │
 └───────────────────┬─────────────────────┘
                     │
                     ▼
              ┌──────────────┐
              │    SNS Topic │
              │ (email alert)│
              └──────────────┘

                        ▼

                   (Local)
     ┌────────────────────────────────┐
     │       ExportClient (Java)      │
     │ - Download consolidated.csv    │
     │ - Filter by (SrcIP, DstIP)     │
     │ - Export export.csv            │
     └────────────────────────────────┘
```
## 📦 3. Structure du projet
```text
awsProject/
 ├── upload-client/
 ├── summarize-worker/
 ├── consolidator-worker/
 ├── export-client/
 ├── jars/                      ← JAR finaux pour EC2
 ├── README.md
```
## 🛠️ 4. Prérequis techniques

### Local
- Java 17
- Maven 3.8+
- AWS CLI 

### AWS
- 1 instance EC2 
- 1 bucket S3 
- 2 files SQS :
  - summarize-queue
  - consolidate-queue
- 1 SNS Topic : iot-alerts-topic

### IAM Role attaché à EC2 :
- s3:GetObject, s3:PutObject
- sqs:ReceiveMessage, sqs:DeleteMessage, sqs:SendMessage
- sns:Publish

---

## 🔧 5. Compilation locale

Dans chaque module :

```bash
cd module-name
mvn clean compile
```

---

## 🎁 6. Génération des JAR pour EC2

### Summarize Worker

```bash
cd summarize-worker
mvn clean package
```

Produit :

```
target/summarize-worker-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Consolidator Worker

```bash
cd consolidator-worker
mvn clean package
```

Produit :

```
target/consolidator-worker-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Déplacez-les dans :  
```
awsProject/jars/
```

---

## 🌩️ 7. Déploiement sur EC2

### Installer Java 17

```bash
sudo dnf install java-17-amazon-corretto
sudo dnf install java-17-amazon-corretto-devel
```

Transférer les JAR  
Avec WinSCP / FileZilla / SFTP dans :

```
~/apps/
```

---

## 🤖 8. Lancer les Workers

### Summarize Worker

```bash
cd ~/apps
java -jar summarize-worker-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Consolidator Worker  
Dans une deuxième session SSH :

```bash
cd ~/apps
java -jar consolidator-worker-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Les deux tournent en boucle, traitent SQS et mettent à jour S3.

---

## 📤 9. UploadClient (local)

```bash
mvn exec:java \
  -Dexec.mainClass="com.iot.project.UploadClient" \
  -Dexec.args="data-20221207.csv iot-traffic-aymane https://sqs.eu-west-3.amazonaws.com/.../summarize-queue"
```

---

## 📁 10. Résultats générés

### Résumé (summaries/*.csv)

```
Date,SrcIP,DstIP,TotalFlowDuration,TotalFwdPkt
```

### Consolidé (consolidated/consolidated.csv)

```
SrcIP,DstIP,Count,MeanFlowDuration,StdFlowDuration,MeanFwdPkt,StdFwdPkt
```

---

## 📧 11. Notification SNS

Lors de la mise à jour du fichier consolidé :

- Le ConsolidatorWorkerAws publie un message SNS.
- Tous les abonnés du Topic (email/SMS) reçoivent une alerte.

---

## 📤 12. ExportClient (local)

Pour filtrer une paire (SrcIP, DstIP) :

```bash
mvn exec:java \
  -Dexec.mainClass="com.iot.project.ExportClient" \
  -Dexec.args="iot-traffic-aymane consolidated/consolidated.csv 192.168.1.10 8.8.8.8"
```

Produit :

```
export.csv
```
## 🧪 13. Test complet du pipeline

1. Lancer les 2 workers EC2
2. Exécuter **UploadClient**
3. **SummarizeWorker** → écrit dans `S3/summaries`
4. Message envoyé vers **consolidate-queue**
5. **ConsolidatorWorker** → génère `consolidated.csv`
6. **SNS** → email reçu
7. **ExportClient** → produit un fichier `export.csv` filtré
