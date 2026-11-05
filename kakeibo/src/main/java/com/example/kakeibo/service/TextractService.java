package com.example.kakeibo.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

@Service
public class TextractService {

    private final TextractClient textractClient;

    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    public List<String> readText(byte[] imageBytes) throws Exception {
        Document document = Document.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))
                .build();

        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(document)
                .build();

        DetectDocumentTextResponse response;
        try {
            response = textractClient.detectDocumentText(request);
        } catch (Exception e) {
            System.out.println("===== Textract接続エラー =====");
            e.printStackTrace();
            System.out.println("=============================");
            throw e;
        }

        List<String> result = new ArrayList<>();
        for (Block block : response.blocks()) {
            if (block.blockType() == BlockType.LINE) {
                result.add(block.text());
            }
        }
        return result;
    }
}
