package com.example.kakeibo.service;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.FeatureType;

@Service
public class TextractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextractService.class);

    // 学内Wi-FiなどでDNSが一時的に引けなくなることがあるため、少し待って再試行します。
    private static final int MAX_ATTEMPTS = 3;

    // 再試行までの待ち時間です。2回目は2秒、3回目は4秒待ちます。
    private static final long RETRY_DELAY_MILLIS = 2_000L;

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

        AnalyzeDocumentResponse response = analyzeWithRetry(request);

        List<String> result = new ArrayList<>();
        for (Block block : response.blocks()) {
            if (block.blockType() == BlockType.LINE) {
                result.add(block.text());
            }
        }
        return result;
    }

    // DNS・接続の一時的な失敗だけを再試行します。
    // 認証エラーなど、待っても直らない失敗はすぐに呼び出し元へ返します。
    private AnalyzeDocumentResponse analyzeWithRetry(AnalyzeDocumentRequest request) {
        SdkClientException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return textractClient.analyzeDocument(request);
            } catch (SdkClientException e) {
                lastError = e;

                if (attempt == MAX_ATTEMPTS || !isTransientNetworkError(e)) {
                    break;
                }

                LOGGER.warn("Textract request failed temporarily; retrying attempt {}/{}: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());

                if (!waitBeforeRetry(RETRY_DELAY_MILLIS * attempt)) {
                    break;
                }
            }
        }

        throw lastError;
    }

    private static boolean isTransientNetworkError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException
                    || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    private static boolean waitBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
