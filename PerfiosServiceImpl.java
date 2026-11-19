package com.sbi.zip.serviceImpl;

import java.io.File;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbi.zip.DAO.PerfiosDetailsDAO;
import com.sbi.zip.configuration.DMSProperties;
import com.sbi.zip.dto.CustomerCsvRecord;
import com.sbi.zip.dto.SearchBean;
import com.sbi.zip.dto.ZipCustomer;
import com.sbi.zip.service.PerfiosService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import java.util.function.Consumer;   // CHANGE (CBS-2): new import
import java.sql.Timestamp;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.CbsBatch;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.CbsLotEntry;   // CHANGE (CBS-3): new import


@Service
public class PerfiosServiceImpl implements PerfiosService {

	private static final Logger log = (Logger) LogManager.getLogger(PerfiosServiceImpl.class);

	@Autowired
	PerfiosDetailsDAO perfiosDAO;

	public static String outputPath = DMSProperties.getInstance().getProperty("OUTPUT_FOLD");
	public static String cleanPath = DMSProperties.getInstance().getProperty("CLEAN_FOLD");

	@Override
	public boolean deleteDirectory(File path) {
		if (path.exists()) {
			File[] files = path.listFiles();

			int n = files.length;
			for (int i = 0;i < n ; i++) {
				if (files[i].isDirectory()) {
					deleteDirectory(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return (path.delete());
	}


	public boolean FileMove(Path src,Path dest) {
		try {
			if (Files.isDirectory(src)) {
				if (!Files.exists(dest)) {
					Files.createDirectories(dest);
				}

				File[] files  = src.toFile().listFiles();

				if (files != null) {
					for (File file : files) {
						if (!FileMove(file.toPath(),dest.resolve(file.getName()))){
							return false;
						}
					}
				}
				Files.delete(src);
			}
			else {
				Files.move(src, dest, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING});
			}
			return true;
		}catch(Exception e) {
			log.error("Move failed");
			log.error("Source : " + src);
			log.error("Destination : " + dest);
			e.printStackTrace();
			return false;
		}
	}

	
	@Override
	public void moveFilesToCleanFolder(String fileName) {
		try {
			File sourceFile = new File(outputPath + File.separator + fileName);
			File destinationFile = new File(cleanPath + File.separator + fileName);
			Files.move(sourceFile.toPath(), destinationFile.toPath());
			log.info("Files moved successfully");
		}catch(Exception e) {
			log.error("Error moving Files",e);
		}
	}


	@Override
	public void HistoryInsert(SearchBean sb) {
		perfiosDAO.HistoryInsert(sb);
	}


	@Override
	public SearchBean getValuesFromDB(String cifNo) {
		return perfiosDAO.getValuesFromDB(cifNo);
	}


	@Override
	public void updateData(String cif, String string) {
		perfiosDAO.updateData(cif, string);
	}


	@Override
	public boolean checkStatusInPerfiosTable(String cifNumber) {
		String cifStatus=perfiosDAO.checkStatusInPerfiosTable(cifNumber);
		log.info("cif status in perfios table " + cifStatus);
		if(cifStatus==null||cifStatus.isEmpty())
			return false;
		return cifStatus.equalsIgnoreCase("ZP")?true:false;
	}

	@Override
	public boolean checkStatusInMainTables(String cifNumber) {
		return perfiosDAO.checkStatusInMAinTables(cifNumber);
	}


	@Override
	public String fetchlotNumber() {
		return perfiosDAO.fetchOldestLotToProcess();
	}
	

	@Override
	public String fetchNewLotNumber(String lcpc) {
		int newLotNumber;
		String lotNumber = perfiosDAO.fetchNewLotToProcess(lcpc);
		log.info("newLotNumber  >>> " + lotNumber);
		try {
			if(lotNumber==null||lotNumber.isEmpty()) lotNumber="0";
			newLotNumber =Integer.parseInt(lotNumber)+1;
		}catch(Exception e) {
			newLotNumber=0;
		}
		log.info("newLotNumber  >>> " + newLotNumber);
		return String.valueOf(newLotNumber);
	}

	
	

	@Override
	public List<Path> fetchCustomersOnLot( Path processingDir, String lcpc) {
		try{
			List<ZipCustomer> zipCustomer= perfiosDAO.fetchCustomerInBatch(lcpc);
			log.info("Number of zipCustomer fetched from database " + zipCustomer.size());
			List<String> fetchLotCustomers= zipCustomer.stream()
					.map(l->new StringBuilder(l.cif_number()+"_"+l.branch_code()+"_"+l.Account_number()).toString())
					.collect(Collectors.toList());
			log.info("Size of the fetchLotCustomers: " + fetchLotCustomers.size());
			log.info("<<<< No of folders  >>> " + fetchLotCustomers.size());
			return fetchLotCustomers.stream().map(processingDir::resolve).toList();

		}catch(Exception e) {
			log.error("");
			return Collections.emptyList();
		}
	}

	@Override
	public CustomerCsvRecord fetchForCsv(String cifNumber,String accountNumber) {
		return perfiosDAO.getEntriesFromDB(cifNumber,accountNumber);
	}

	@Override
	public void updateStatusAndHistory(List<CustomerCsvRecord> customerFolders, Path lotName, String lotNo,String lcpc) {
		log.info("lotName " + lotName.toString());
		perfiosDAO.updateZipStatus(customerFolders,lotName.toString());
		
		perfiosDAO.insertHistory(customerFolders,lotName.toString());
		
		perfiosDAO.updateLotHistory(lotName.toString(),lotNo,lcpc);
		
	}




	// ===================== CHANGE (ZF): new method =====================
	@Override
	public void markZipFailed(String cifNumber, String accountNumber, String remark) {
		perfiosDAO.updateZipFailed(cifNumber, accountNumber, remark);
	}
	// ===================== END CHANGE (ZF) =====================

	// ===================== CHANGE (CBS) - CR 26100024: new method =====================
	@Override
	public void insertCbsRecord(java.sql.Date dumpDate, String lotNo, String fileName, String lcpc, int noOfRecords) {
		perfiosDAO.insertCbsRecord(dumpDate, lotNo, fileName, lcpc, noOfRecords);
	}
	// ===================== END CHANGE (CBS) =====================

	// ===================== CHANGE (CBS-2): new method =====================
	@Override
	public void streamCbsDump(String lotName, Consumer<String[]> onHeader, Consumer<String[]> onRow) {
		perfiosDAO.streamCbsDump(lotName, onHeader, onRow);
	}
	// ===================== END CHANGE (CBS-2) =====================

	// ===================== CHANGE (CBS-3): new methods =====================
	@Override
	public CbsBatch findOpenCbsBatch() {
		return perfiosDAO.findOpenCbsBatch();
	}

	@Override
	public int nextCbsBatchNo(String ddMMyy) {
		return perfiosDAO.nextCbsBatchNo(ddMMyy);
	}

	@Override
	public int cbsRecordsDoneForLot(String lotZipName) {
		return perfiosDAO.cbsRecordsDoneForLot(lotZipName);
	}

	@Override
	public void insertCbsBatchRecord(Timestamp at, String lotNo, String batchFile, String lcpc,
			int count, int batchNo, String lotZipName) {
		perfiosDAO.insertCbsBatchRecord(at, lotNo, batchFile, lcpc, count, batchNo, lotZipName);
	}

	@Override
	public void closeCbsBatch(String batchFile) {
		perfiosDAO.closeCbsBatch(batchFile);
	}

	@Override
	public List<CbsLotEntry> cbsBatchLots(String batchFile) {
		return perfiosDAO.cbsBatchLots(batchFile);
	}
	// ===================== END CHANGE (CBS-3) =====================



}
