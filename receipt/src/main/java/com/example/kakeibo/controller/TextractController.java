package com.example.kakeibo.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.kakeibo.service.TextractService;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/textract")
public class TextractController {

    private final TextractService textractService;

    public TextractController(TextractService textractService) {
        this.textractService = textractService;
    }

    @PostMapping(value = "/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> readText(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("loggedIn"))
                || session.getAttribute("expenseUserId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ログインしてください");
        }
        try {
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "画像を選択してください");
            }

            String contentType = file.getContentType();
            if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "PNGまたはJPEG画像を指定してください");
            }

            List<String> texts = textractService.readText(file.getBytes());
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("text", texts);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "文字認識に失敗しました", e);
        }
    }
}
