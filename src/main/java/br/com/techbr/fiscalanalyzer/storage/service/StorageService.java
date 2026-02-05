package br.com.techbr.fiscalanalyzer.storage.service;

import java.io.InputStream;

public interface StorageService {
    void put(String key, InputStream content, long contentLength, String contentType);
    InputStream get(String key);
    boolean exists(String key);
}
