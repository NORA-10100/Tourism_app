package com.example.kakeibo.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.FeatureType;

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

        // DetectDocumentText is limited to a small set of Latin-script
        // languages. AnalyzeDocument with LAYOUT supports Japanese receipts.
        AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                .document(document)
                .featureTypes(FeatureType.LAYOUT)
                .build();

        AnalyzeDocumentResponse response;
        try {
            response = textractClient.analyzeDocument(request);
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
