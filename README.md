# AI-Based Notification Priority Manager

An intelligent Android application that classifies incoming notifications into priority levels using a fine-tuned DistilBERT model optimized for mobile deployment.

---

## Overview

Modern smartphones receive a large number of notifications, many of which are irrelevant or low priority. This project aims to solve this problem by using Natural Language Processing (NLP) to automatically classify notifications based on their importance.

The system processes notification text in real time and assigns it a priority level, enabling smarter notification management.

---

## Objectives

* Classify notifications into multiple priority levels (e.g., urgent, important, low priority)
* Perform on-device inference without relying on cloud APIs
* Optimize model performance for mobile environments
* Demonstrate end-to-end integration of AI and Android

---

## Tech Stack

### Machine Learning

* Python
* Hugging Face Transformers
* PyTorch
* Scikit-learn

### Model Optimization

* ONNX (Open Neural Network Exchange)
* Quantization (for size and speed optimization)

### Android Development

* Java / Kotlin
* Android Studio
* ONNX Runtime (Android)

---

## Dataset and Preprocessing

* Combined multiple datasets:

  * Spam Detection Dataset
  * Email Classification Dataset

### Preprocessing Steps:

* Text cleaning (removal of special characters, noise)
* Lowercasing and normalization
* Tokenization using DistilBERT tokenizer

### Data Split:

* Training: 80%
* Testing: 20%

---

## Model Architecture

* DistilBERT (Transformer-based model)

  * Lightweight version of BERT
  * Faster and more efficient
  * Captures contextual meaning of text

### Why DistilBERT?

* Reduced size compared to BERT
* Faster inference
* Suitable for mobile deployment

---

## Training Details

* Fine-tuned on combined dataset
* Loss Function: Cross-Entropy Loss
* Optimizer: AdamW
* Evaluation Metric: Accuracy

### Baseline Model:

* Naive Bayes: ~96% accuracy

---

## Model Optimization

To deploy the model on mobile, the following optimizations were performed:

### 1. ONNX Conversion

* Converted trained PyTorch model to ONNX format
* Enables cross-platform compatibility

### 2. Quantization

* Reduced precision from float32 to int8
* Significantly reduces model size and improves speed

### Results:

* Model size reduced from 255 MB to 64 MB
* Faster inference on Android devices

---

## Android Integration

The optimized model was integrated into an Android application.

### Key Components:

#### ModelRunner

* Loads ONNX model from assets
* Initializes ONNX Runtime
* Runs inference

#### Tokenizer

* Converts text to token IDs
* Uses vocab.txt
* Adds special tokens and attention masks

#### NotificationListener

* Captures real-time notifications
* Extracts title and message
* Sends text for classification

---

## Working Pipeline

1. Notification received on device
2. NotificationListener captures content
3. Text is preprocessed and tokenized
4. Input tensors created (input_ids, attention_mask)
5. ONNX model performs inference
6. Output mapped to priority level
7. Priority used to manage notification behavior

---

## Testing and Validation

* Tested using:

  * Hardcoded sample texts
  * Real-time notifications via emulator

### Debugging:

* Logcat used to monitor:

  * Notification content
  * Model predictions

---

## Key Features

* Real-time notification classification
* On-device AI (no internet required)
* Lightweight optimized model
* End-to-end ML and Android integration

---

## Project Structure

```
ai-notification-manager/
│
├── model_training.ipynb
├── model_onnx_quantized.ipynb
├── android_app/
│   ├── assets/
│   │   ├── model_quantized.onnx
│   │   ├── vocab.txt
│   │   └── label_map.json
│   ├── ModelRunner.java
│   ├── Tokenizer.java
│   └── NotificationListener.java
```

---

## Key Learnings

* Transformer-based NLP models (DistilBERT)
* Model optimization for edge devices
* ONNX and quantization techniques
* Android and AI integration
* Real-time system design

---

## Future Enhancements

* Add more fine-grained priority levels
* Personalization based on user behavior
* Notification auto-filtering or blocking
* UI for user control and feedback
* Cloud sync and analytics

---

## Contributors

* Sai Cheranjeeve S
* Sivapriya Sivadasan
* Sreelakshmi R
* Tanya Mariam Viji

---

## Conclusion

This project demonstrates how modern NLP models can be effectively deployed on mobile devices to solve real-world problems. By combining machine learning, optimization, and Android development, we created a scalable and efficient notification management system.

