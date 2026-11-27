package com.example.kakeibo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.kakeibo.service.TextractService;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

class TextractServiceTests {

    @Test
    void retriesWhenDnsLookupFailsTemporarily() throws Exception {
        TextractClient client = mock(TextractClient.class);

        when(client.analyzeDocument(any(AnalyzeDocumentRequest.class)))
                .thenThrow(SdkClientException.create(
                        "Unable to execute HTTP request",
                        new UnknownHostException("textract.ap-northeast-1.amazonaws.com")))
                .thenReturn(AnalyzeDocumentResponse.builder()
                        .blocks(Block.builder()
                                .blockType(BlockType.LINE)
                                .text("はま寿司")
                                .build())
                        .build());

        TextractService service = new TextractService(client);

        List<String> lines = service.readText(new byte[] {1, 2, 3});

        assertEquals(List.of("はま寿司"), lines);
        verify(client, times(2)).analyzeDocument(any(AnalyzeDocumentRequest.class));
    }

    @Test
    void doesNotRetryCredentialErrors() {
        TextractClient client = mock(TextractClient.class);

        when(client.analyzeDocument(any(AnalyzeDocumentRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials"));

        TextractService service = new TextractService(client);

        assertThrows(SdkClientException.class,
                () -> service.readText(new byte[] {1, 2, 3}));
        verify(client, times(1)).analyzeDocument(any(AnalyzeDocumentRequest.class));
    }
}
