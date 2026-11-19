package com.sbi.zip.dto;

// CHANGE (CBS-3): new class - one CBS batch: dump zip name, batch number and the records already in it
public record CbsBatch(String fileName, int batchNo, long records) {
}
