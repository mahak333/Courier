package com.mahak.courier.service;

import com.mahak.courier.repository.ShareRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class CodeGeneratorService {

    private final ShareRepository shareRepository;

    private static final int MAX_RETRY = 5;
    private static final int CODE_MIN = 100000;
    private static final int CODE_MAX = 999999;

    public CodeGeneratorService(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public String generateUniqueCode() {
        String code;
        int attempts = 0;

        do {
            code = generateRandomCode();
            attempts++;
            if (attempts > MAX_RETRY) {
                throw new IllegalStateException(
                        "Failed to generate a unique code after " + MAX_RETRY + " attempts"
                );
            }
        } while (shareRepository.existsByCode(code));

        return code;
    }

    private String generateRandomCode() {
        int number = ThreadLocalRandom.current().nextInt(CODE_MIN, CODE_MAX + 1);
        return String.valueOf(number);
    }
}