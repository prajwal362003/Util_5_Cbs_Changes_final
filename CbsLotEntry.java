package com.sbi.zip.dto;

import java.sql.Timestamp;

// CHANGE (CBS-3): new class - one lot (or the part of a lot) inside a CBS batch, used for the manifest
public record CbsLotEntry(Timestamp at, String lcpc, String lotNo, String lotZipName, int count) {
}
