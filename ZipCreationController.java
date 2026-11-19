package com.sbi.zip.controller;

import java.io.BufferedReader;   // CHANGE (CBS): new import
import java.io.BufferedWriter;   // CHANGE (CBS): new import
import java.time.LocalDateTime;   // CHANGE (CBS-2): new import
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;   // CHANGE (CBS): new import
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;   // CHANGE (CBS-3): new import
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;   // CHANGE (CBS): new import
import java.util.List;
import java.util.Locale;
import java.util.Comparator;   // CHANGE (CBS-3): new import
import java.util.regex.Matcher;   // CHANGE (CBS-3): new import
import java.util.regex.Pattern;   // CHANGE (CBS-3): new import
import java.util.zip.ZipEntry;   // CHANGE (CBS-3): new import
import java.util.zip.ZipOutputStream;   // CHANGE (CBS-3): new import
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;   // CHANGE (CBS): new import
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.sbi.constants.FilePaths;
import com.sbi.zip.DAO.PerfiosDetailsDAO;
import com.sbi.zip.configuration.DMSProperties;
import com.sbi.zip.dto.CbsBatch;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.CbsLotEntry;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.Customer;
import com.sbi.zip.dto.CustomerCsvRecord;
import com.sbi.zip.service.PerfiosService;
import com.sbi.zip.utils.FolderIdentifier;

@Controller
public class ZipCreationController {

	@Autowired
	PerfiosService perfiosService;

	@Autowired 
	PerfiosDetailsDAO perfiosDetailsDAO;

	private static final Logger logger = (Logger) LogManager.getLogger(ZipCreationController.class);
	static String docDate = new SimpleDateFormat("dd.MM.yyyy_HH.mm").format(new Date());

	public static String docFilePath = DMSProperties.getInstance().getProperty("DOC_FILE_PATH") + "/"
			+ "FI_UTIL5_SUCCESS_DOC_FILE_" + DMSProperties.getInstance().getProperty("UTIL_NUM") + "_1_" + docDate
			+ ".txt"; // GBSS_REPORTS_DOC_FILE_1_21.11.2020_14.17.txt
	public static String docFilePathExp = DMSProperties.getInstance().getProperty("DOC_FILE_PATH") + "/"
			+ "FI_UTIL5_EXCEPTION_DOC_FILE_" + DMSProperties.getInstance().getProperty("UTIL_NUM") + "_1_" + docDate
			+ ".txt"; // GBSS_REPORTS_DOC_FILE_1_21.11.2020_14.17.txt
	public static String docFilePathResponse = DMSProperties.getInstance().getProperty("RES_DOC_FILE_PATH");

	public static int batchSize=Integer.parseInt(DMSProperties.getInstance().getProperty("BATCH_SIZE"));

	public static String outputPath = DMSProperties.getInstance().getProperty("OUTPUT_FOLD");
	public static String cleanPath = DMSProperties.getInstance().getProperty("CLEAN_FOLD");
	public static String zipOutputDirPath=DMSProperties.getInstance().getProperty("Zip.Output");
	public static String cbsOutputDirectoryPath=DMSProperties.getInstance().getProperty("Cbs.Data");   // CHANGE (CBS): uncommented, add Cbs.Data in properties file
	public static String cbsManifestDirectoryPath=DMSProperties.getInstance().getProperty("Cbs.Manifest");   // CHANGE (CBS-3): new property Cbs.Manifest = manifest_file folder


	long staleProcessingMinutes=1;
	long stabilityWindowSeconds=60;

	//private int clusterSize=1;
//	private int serverIndex=0;
	String lcpcLists=DMSProperties.getInstance().getProperty("LCPC_NAME");

	String inputPath = FilePaths.inputFolder;
	String backupPath = FilePaths.BackupFolder;
	String Exception = FilePaths.ExceptionFolder;
	String cleanFolder = FilePaths.cleanFolder;
	String exceptionFolder = FilePaths.ExceptionFolder;
	String processigPath=FilePaths.processingPath;

	private final String hostId = resolveHostId();

	private static String resolveHostId() {
		try {
			return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
		} catch (Exception e) {
			return "unknown-" + ProcessHandle.current().pid();
		}
	}

	public void processLcpc() throws IOException {
		Set<String> lcpcSet=Arrays.stream(lcpcLists.split(",")).collect(Collectors.toSet());
		for(String lcpc: lcpcSet) {
			String inputPathLcpc=inputPath+"/"+lcpc;
			String inputProcessPathLcpc=processigPath+"/"+lcpc;
		//	zipCreate(inputPathLcpc,inputProcessPathLcpc,lcpc);
			processAvailableCustomer(inputPathLcpc,inputProcessPathLcpc,lcpc);
		}	
	}
	
	
	public void processAvailableCustomer(String inputPathLcpc, String inputProcessPathLcpc, String lcpc){
		logger.info("Inside processAvailableCustomer");
		while(true) {
		//	String newLotNumber = perfiosService.fetchNewLotNumber(lcpc);
		//	logger.info("newLotNumber " + newLotNumber);

			List<Path> customerFolders=perfiosService.fetchCustomersOnLot(Paths.get(inputPathLcpc),lcpc);
			if (customerFolders.size()==0) {
				logger.info("No records found for the LCPC  "+lcpc);
				
				break;
			}
			try{
				zipCreate(inputPathLcpc,inputProcessPathLcpc,lcpc,customerFolders);
				logger.info("zipCreated for a lot in the folder   "+lcpc);
			}catch(Exception e) {
				logger.error(" Error from zipCreate    "+e.getMessage());
				break;
			}
			logger.info("No of customer folders found " + customerFolders.size());
		}
	}
	

	public void zipCreate(String inputPathLcpc, String inputProcessPathLcpc, String lcpc, List<Path> customerFolders) throws IOException {
		logger.info("__________________________________________________"+lcpc);
		logger.info("Inside MAIN of Utility....create doc file..............");

		File ExceptionFold = new File(Exception);
		logger.info("The input folder path is :"  + inputPath);
		logger.info("The Backup folder path is :"  + backupPath);
		logger.info("The Exception folder path is :"  + Exception);

		File CleanFolder = new File(cleanFolder);
		String currentDate=parsedCurrentdate();

	//	Path inputProcessingDirectory=Paths.get(inputPathLcpc);
		Path processingDir = Paths.get(inputProcessPathLcpc);

		String newLotNumber = perfiosService.fetchNewLotNumber(lcpc);
		logger.info("newLotNumber " + newLotNumber);

	/*	List<Path> customerFolders=perfiosService.fetchCustomersOnLot(inputProcessingDirectory,lcpc);
		if (customerFolders.size()==0) {
			logger.info("No records found for the LCPC  "+lcpc);
			return;
		}
		logger.info("No of customer folders found " + customerFolders.size());*/

		
		
		
		
		
		
		
		
		Path zipOutputDir = Paths.get(zipOutputDirPath);
		Files.createDirectories(zipOutputDir);

		String lotNo="LOT"+newLotNumber;//lotNumber.split("_")[4];
		logger.info("lot to process now " + lotNo);

		Path tempZip = zipOutputDir.resolve(lcpc+"_"+ lotNo + "_" + currentDate + ".zip");
		logger.info("tempZip" + tempZip);
		
		logger.info("lcpc" + lcpc +" lotNo"+lotNo);

		Path csvCustTempFile=zipOutputDir.resolve(lcpc+"_"+ lotNo + "_"+currentDate+"_CustomerList.csv");
		logger.info("csv Cust TempFile" + csvCustTempFile);
		
		Path csvTempZipFile = zipOutputDir.resolve(lcpc+"_"+ lotNo + "_" + currentDate + "_Details.zip");
		logger.info("csvTempZipFile" + csvTempZipFile);
		// CHANGE (CBS): csvDetailsTempFile (details csv path + log) removed from here - details rows now go to the cbs_data zip

		String lotNameToSend =lcpc+"_"+ lotNo + "_" + currentDate;
		logger.info("lotNameToSend " + lotNameToSend);
		
		
		
		
		// CHANGE (CBS-3): the per-day cbs paths/names were removed from here - batch file names and folders are decided inside addLotToCbsBatches
		
		try {
			List<CustomerCsvRecord> csvRecords = new ArrayList<>();
			List<CustomerCsvList> csvCustList = new ArrayList<>();

			String password=getPassword();
			ZipParameters zipParameters = new ZipParameters();
			zipParameters.setEncryptFiles(true);
			zipParameters.setEncryptionMethod(EncryptionMethod.AES);
			zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

			try (ZipFile zipFile = new ZipFile(tempZip.toFile(), password.toCharArray())) {
				//				Path csvFile = tempDir.resolve("MUM_LOT0_.csv");
				for (Path customerFolder : customerFolders) {
					String customerId = customerFolder.getFileName().toString();
					logger.info("CustomerId : " + customerId);
					
					if(!(Files.exists(customerFolder)&&Files.isDirectory(customerFolder))) {
						logger.info("Files exists > "+Files.exists(customerFolder)+"\t Files.isDirectory > "+Files.isDirectory(customerFolder));
						// ===== CHANGE (ZF): mark ZF + history when folder is missing =====
						markZipFailed(customerId, "Zip not created - folder missing in input");
						// ===== END CHANGE (ZF) =====
						continue;
					}
					
					Path claimed = claimFolder(customerFolder, processingDir);					
					if (claimed == null)  continue;
					
					CleanFolder = new File(cleanFolder + "/" + lcpc+ "/" +customerId);
					logger.info("CleanFolder Path is "+ CleanFolder.getAbsolutePath());
					
					if (!(CleanFolder.exists())) CleanFolder.mkdir();
					
					ExceptionFold = new File(exceptionFolder + "/" + lcpc+ "/" + customerId);
					logger.info("Exception Folder Path is " + ExceptionFold.getAbsolutePath());
					
					if (!ExceptionFold.exists()) {
						logger.info("The Exception Folder does not exists");
						ExceptionFold.mkdir();
					}
					String[] folderValues=customerId.split("_");
					Customer customer = new Customer(folderValues[0],folderValues[1],folderValues[2]);
					try {
						FolderIdentifier.parse(customerId);
					} catch (IllegalArgumentException badName) {
						//	remarks="INVALID_FOLDER_NAME: " + badName.getMessage();
						moveValidatedFolderToClean(claimed, ExceptionFold.toPath());
						deleteFolderFromProcessing(claimed);
						continue;
					}
					boolean zipCreateStatus = perfiosService.checkStatusInPerfiosTable(customer.cifNumber());
					logger.info("cif status in perfios table " + zipCreateStatus);
					if (!zipCreateStatus) {
						logger.info("ZIP already created for a CIF number {}",customer.cifNumber());
						moveValidatedFolderToClean(claimed, ExceptionFold.toPath());
						deleteFolderFromProcessing(claimed);
						continue;
					}
					boolean cifNotUnderProcess=perfiosService.checkStatusInMainTables(customer.cifNumber());
					logger.info("cifNotUnderProcess " + cifNotUnderProcess);
					if (!cifNotUnderProcess) {
						logger.info("CIF already under processing for {}", customer.cifNumber());
						moveValidatedFolderToClean(claimed, ExceptionFold.toPath());
						deleteFolderFromProcessing(claimed);
						continue;
					}
					logger.info("customerFolder : " + processingDir);
					if (!Files.isDirectory(processingDir)) {
						throw new IllegalStateException("Lot " +lotNo+": output folder missing for cif " +customer.cifNumber()+" ("+customerFolder+") despite status=PROCESSED");
					}
					logger.info("Clean Folder Path : " + CleanFolder.getAbsolutePath());                  
					Path cleanTarget = moveValidatedFolderToClean(claimed, CleanFolder.toPath());
					logger.info("Customer folder pushed to clean location {}", cleanTarget);
					//logger.info("cleanTarget: " + cleanTarget.getFileName());

					try {
						zipFile.addFolder(cleanTarget.toFile(), zipParameters);
						logger.info("Folder added successfully");
					}catch(IOException e) {
						logger.error("Error while adding folder to the zip",e);
					}
					//Modified
					logger.info("Before adding in the csvRecords: " + csvRecords.size());

					csvRecords.add(perfiosService.fetchForCsv(customer.cifNumber(),customer.accountNumber()));
					logger.info("After adding in the csvRecords: " + csvRecords.size());

					csvCustList.add(new CustomerCsvList(lotNameToSend, customer.cifNumber(), customer.accountNumber()));
					logger.info("After adding in the csvCustList: " + csvCustList.size());

					deleteFolderFromProcessing(claimed);
					logger.info("Iterated for the customer  "+customer);
				}
				writeCustCsv(csvCustTempFile, csvCustList);
				logger.info("After adding in the csvCustTempFile: " + csvCustTempFile);
				// CHANGE (CBS): writeCsv(csvDetailsTempFile, csvRecords) removed here - details rows are added to the cbs_data zip below
				//zipFile.addFile(csvTempFile.toFile(), zipParameters);
			}
			logger.info("__________________________ "); 
			
			// CHANGE (CBS): details check now uses csvRecords.isEmpty(), customer list check uses hasDataRows
			boolean detailsHasData = !csvRecords.isEmpty();
			logger.info("details has Data >> "+detailsHasData);
			boolean custHasData = hasDataRows(csvCustTempFile);
			logger.info("customer list has Data >> "+custHasData);
			
			if(!detailsHasData&&!custHasData) {
				logger.info("no records and customer csv has only header. Deleting temporary csv file ");
				Files.deleteIfExists(csvCustTempFile);
				return;
			}
			
			//try (ZipFile zipcsvFile = new ZipFile(csvTempZipFile.toString(),password.toCharArray())) {
			// CHANGE (CBS): this zip now holds ONLY the customer list csv (details csv no longer added)
			try (ZipFile zipcsvFile = new ZipFile(csvTempZipFile.toFile(),password.toCharArray())) {
				logger.info("zip csv file \n "+csvCustTempFile.toFile());
				
				zipParameters.setFileNameInZip(csvCustTempFile.getFileName().toString());
				zipcsvFile.addFile(csvCustTempFile.toFile(), zipParameters);
			}
			logger.info("!!!!!! ");

			Files.deleteIfExists(csvCustTempFile);

			Path finalZip = zipOutputDir.resolve(tempZip);
			logger.info("finalZip "+finalZip);
			logger.info("tempzip " + tempZip);
			try{
				logger.info("tempZip exists "+ Files.exists(tempZip));
				logger.info("tempzip size :" + Files.size(tempZip));
				Files.move(tempZip, finalZip, StandardCopyOption.ATOMIC_MOVE);
				logger.info("finalZip "+finalZip);
			}catch(Exception e) {
				logger.info("Inside catch "+e.getMessage());
				//e.printStackTrace();
			}
			Path finalCsvZip = zipOutputDir.resolve(csvTempZipFile);
			Files.move(csvTempZipFile, finalCsvZip, StandardCopyOption.ATOMIC_MOVE);
			logger.info("finalCsvZip "+finalCsvZip);

			perfiosService.updateStatusAndHistory(csvRecords,finalZip.getFileName(),newLotNumber,lcpc);

			// ===== CHANGE (CBS-3): batch wise CBS dump + manifest =====
			// Runs after updateStatusAndHistory because the PSO query needs status ZC.
			// Best effort: the lot zip and its status are already final, so a failure is logged and the loop continues.
			String lotZipName = finalZip.getFileName().toString();
			try {
				for (int attempt = 1; ; attempt++) {
					try {
						addLotToCbsBatches(lotZipName, lcpc, lotNo);   // safe to repeat: it resumes where the lot stopped
						break;
					} catch (IOException ex) {
						if (attempt >= 3) throw ex;
						logger.warn("CBS batch step attempt {} failed, retrying: {}", attempt, ex.getMessage());
						sleepBackoff(attempt * 5);
					}
				}
			} catch (Exception e) {
				logger.error("CBS batch step FAILED for lot zip {} (lcpc={}, lotNo={}). Lot zip and ZC status are already done - this lot's CBS rows still have to be added (catch-up).",
						lotZipName, lcpc, lotNo, e);
			}
			// ===== END CHANGE (CBS-3) =====

			logger.info("End of functionality ");
		}catch(Exception e) {
			logger.info("Inside catch ",e);
			e.printStackTrace();
			// ===== CHANGE (CBS): rethrow so the loop stops instead of repeating =====
			// do not swallow: lets processAvailableCustomer stop instead of re-fetching the same customers forever
			throw new IOException("zipCreate failed for lcpc " + lcpc, e);
			// ===== END CHANGE (CBS) =====
		}
	}

	private boolean hasDataRows(Path csvFile) throws IOException {
		try(Stream<String> lines=Files.lines(csvFile)){
			return lines.skip(1).anyMatch(line->!line.trim().isEmpty());
		}
	}

	private void deleteFolderFromProcessing(Path processing) {
		if (processing == null || !Files.exists(processing)) return; 
		try {
			Files.walkFileTree(processing, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					if (exc != null) throw exc;
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to delete folder: " + processing, e);
		}
		logger.info("removed: " + processing.toString());
	}

	private static String parsedCurrentdate() {
		LocalDate today = LocalDate.now(ZoneId.systemDefault());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
		return today.format(formatter);
	}

	private void writeCustCsv(Path csvCustTempFile, List<CustomerCsvList> csvCustList) {
		logger.info("Inside writeCSV");
		StringBuilder sb = new StringBuilder();
		logger.info("sb: " + sb.toString());

		sb.append("LOT_NAME,CIF_NUMBER,ACCOUNT_NUMBER").append(System.lineSeparator());
		for (CustomerCsvList r : csvCustList) {
			sb.append(csvEscape(r.lotFileName())).append(',')
			.append(csvEscape(r.cif())).append(',')
			.append(csvEscape(r.accountNumber())).append(',')
			.append(System.lineSeparator());
		}

		logger.info("sb after appending entries: "/* ,sb.toString() */);
		try {
			Files.writeString(csvCustTempFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}catch(Exception e) {
			logger.info("csv Write exception: ",e);
		}
	}
	
	// ===================== CHANGE (CBS-3) - batch wise CBS dump + manifest =====================
	// Rules: a batch = up to <recordsPerBatch> records (jar argument). Lots are added in order, a lot can be split across two batches
	// (exact cap). A batch stays OPEN (plain csv in <Cbs.Data>/OPEN) until it is full, then it is zipped into <Cbs.Data> as
	// CBS_DUMP_<ddMMyy>_<N>.zip and its manifest Manifest_<ddMMyy>_<N>.xlsx is written in <Cbs.Manifest>.
	// N restarts at 1 for each date (date = day the batch was started). State is kept in kyc_cbs_perfios_records.

	private static final String CBS_DELIMITER = ",";   // change to "\t" if the client wants tab separated files
	private static final boolean CBS_REPLACE_COMMAS = true;   // commas inside a value become a space so every value stays in ONE column (client to confirm); false = keep commas and rely on quotes
	private static final long CBS_LOCK_WAIT_MS = 10 * 60 * 1000L;   // max wait for another run to finish
	private static final long CBS_LOCK_STALE_MS = 15 * 60 * 1000L;  // lock older than this = crashed run
	private static final String BATCH_USAGE = "Usage: java -jar Util5.jar <recordsPerBatch> [close]   e.g.  java -jar Util5.jar 200000";
	private static final String[] MANIFEST_HEADER = {"DATE", "LOT", "Date & Time", "File", "Count", "Password"};
	private static final DateTimeFormatter CBS_NAME_DAY = DateTimeFormatter.ofPattern("ddMMyy");
	private static final DateTimeFormatter CBS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter CBS_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy h:mm a", Locale.ENGLISH);
	private static final Pattern CBS_BATCH_NAME = Pattern.compile("^CBS_DUMP_(\\d{6})_(\\d+)\\.zip$");
	private static final Pattern LOT_ZIP_DAY = Pattern.compile("_(\\d{8})\\.zip$");

	private static volatile int cbsBatchSize = 0;   // records per batch, set from the jar command

	/** First jar argument = records per batch (mandatory). Call this at the very start of main. */
	public static int parseBatchSize(String[] args) {
		if (args == null || args.length == 0 || args[0] == null || args[0].trim().isEmpty()) {
			throw new IllegalArgumentException("Batch size is missing. " + BATCH_USAGE);
		}
		int n;
		try {
			n = Integer.parseInt(args[0].trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Batch size '" + args[0] + "' is not a number. " + BATCH_USAGE);
		}
		if (n <= 0) throw new IllegalArgumentException("Batch size must be greater than 0. " + BATCH_USAGE);
		cbsBatchSize = n;
		return n;
	}

	/** Optional second argument "close" = close the open batch by hand (no customers are processed). */
	public static boolean isCloseRequested(String[] args) {
		return args != null && args.length > 1 && args[1] != null && "close".equalsIgnoreCase(args[1].trim());
	}

	/** Closes the open batch even if it is not full: dump zip + manifest are created and the batch is marked CLOSED. */
	public void closeOpenBatchNow() throws IOException {
		Path dataDir = Paths.get(cbsOutputDirectoryPath);
		Files.createDirectories(dataDir);
		Path lock = dataDir.resolve("cbs_batch.lock");
		acquireCbsLock(lock);
		try {
			CbsBatch open = perfiosService.findOpenCbsBatch();
			if (open == null) {
				logger.info("close requested but there is no open CBS batch");
				return;
			}
			closeCbsBatch(open);
		} finally {
			try { Files.deleteIfExists(lock); } catch (IOException e) { logger.warn("could not release lock {}: {}", lock, e.getMessage()); }
		}
	}

	/**
	 * Adds one lot's CBS rows (PSO query, filtered by the lot zip name) to the open batch.
	 * Rows are sorted by CIF + account so a split between two batches is always the same, and a repeat
	 * (retry) resumes after the rows already recorded for this lot.
	 */
	private void addLotToCbsBatches(String lotZipName, String lcpc, String lotNo) throws IOException {
		if (cbsBatchSize <= 0) throw new IOException("CBS batch size is not set (first jar argument)");
		Path dataDir = Paths.get(cbsOutputDirectoryPath);
		Path openDir = dataDir.resolve("OPEN");
		Files.createDirectories(openDir);
		Path lock = dataDir.resolve("cbs_batch.lock");
		acquireCbsLock(lock);
		try {
			List<String[]> rows = new ArrayList<>();
			final String[][] header = new String[1][];
			perfiosService.streamCbsDump(lotZipName, h -> header[0] = h, rows::add);
			if (rows.isEmpty()) {
				logger.info("No CBS rows for lot zip {} (customers missing in the customer/address tables?)", lotZipName);
				return;
			}
			rows.sort(Comparator.comparing((String[] r) -> r[0]).thenComparing(r -> r[1]));   // CIF_1, ACCT_NO_2

			int done = perfiosService.cbsRecordsDoneForLot(lotZipName);
			if (done >= rows.size()) {
				logger.info("Lot zip {} is already in the CBS batches ({} rows)", lotZipName, done);
				return;
			}
			List<String[]> todo = new ArrayList<>(rows.subList(done, rows.size()));

			Timestamp now = new Timestamp(System.currentTimeMillis());
			CbsBatch batch = perfiosService.findOpenCbsBatch();
			if (batch != null && batch.records() >= cbsBatchSize) {   // full batch left behind by an earlier crash
				closeCbsBatch(batch);
				batch = null;
			}

			int pos = 0;
			while (pos < todo.size()) {
				if (batch == null) {   // start a new batch: date = today, N restarts at 1 every day
					String day = LocalDate.now().format(CBS_NAME_DAY);
					int n = perfiosService.nextCbsBatchNo(day);
					batch = new CbsBatch("CBS_DUMP_" + day + "_" + n + ".zip", n, 0);
					logger.info("Started CBS batch {}", batch.fileName());
				}
				int take = (int) Math.min((long) cbsBatchSize - batch.records(), (long) todo.size() - pos);
				appendRowsToOpenCsv(openDir, batch, header[0], todo.subList(pos, pos + take));
				perfiosService.insertCbsBatchRecord(now, lotNo, batch.fileName(), lcpc, take, batch.batchNo(), lotZipName);
				batch = new CbsBatch(batch.fileName(), batch.batchNo(), batch.records() + take);
				pos += take;
				logger.info("CBS batch {} : +{} rows from {} (batch now {} / {})", batch.fileName(), take, lotZipName, batch.records(), cbsBatchSize);
				if (batch.records() >= cbsBatchSize) {   // batch is full: finish it
					closeCbsBatch(batch);
					batch = null;
				}
			}
		} finally {
			try { Files.deleteIfExists(lock); } catch (IOException e) { logger.warn("could not release lock {}: {}", lock, e.getMessage()); }
		}
	}

	private void appendRowsToOpenCsv(Path openDir, CbsBatch batch, String[] header, List<String[]> part) throws IOException {
		Path csv = openDir.resolve(batch.fileName().replaceAll("\\.zip$", ".csv"));
		boolean newFile = Files.notExists(csv);
		try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			if (newFile) { w.write(joinCbs(header)); w.newLine(); }
			for (String[] r : part) { w.write(joinCbs(r)); w.newLine(); }
		}
	}

	/** zip the batch csv (AES, same name inside), write the manifest, mark the batch CLOSED. Safe to repeat. */
	private void closeCbsBatch(CbsBatch batch) throws IOException {
		Matcher m = CBS_BATCH_NAME.matcher(batch.fileName());
		if (!m.matches()) throw new IOException("Unexpected CBS batch file name: " + batch.fileName());
		String day = m.group(1);
		String dumpPassword = getPassword(LocalDate.parse(day, CBS_NAME_DAY));   // password of the batch start date

		Path dataDir = Paths.get(cbsOutputDirectoryPath);
		Path openCsv = dataDir.resolve("OPEN").resolve(batch.fileName().replaceAll("\\.zip$", ".csv"));
		Path zip = dataDir.resolve(batch.fileName());
		Path tmpZip = dataDir.resolve(batch.fileName() + ".tmp");

		if (Files.exists(openCsv)) {
			Files.deleteIfExists(tmpZip);
			ZipParameters zp = new ZipParameters();
			zp.setEncryptFiles(true);
			zp.setEncryptionMethod(EncryptionMethod.AES);
			zp.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
			zp.setCompressionLevel(CompressionLevel.FAST);
			zp.setFileNameInZip(openCsv.getFileName().toString());
			try (ZipFile z = new ZipFile(tmpZip.toFile(), dumpPassword.toCharArray())) {
				z.addFile(openCsv.toFile(), zp);
				if (!z.isValidZipFile()) throw new IOException("New CBS zip failed validation: " + tmpZip);
			}
			Files.move(tmpZip, zip, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} else if (Files.notExists(zip)) {
			throw new IOException("Open csv of batch " + batch.fileName() + " is missing: " + openCsv);
		}

		writeManifest(batch, day, m.group(2), dumpPassword);
		perfiosService.closeCbsBatch(batch.fileName());
		Files.deleteIfExists(openCsv);
		logger.info("CBS batch {} closed ({} records)", batch.fileName(), batch.records());
	}

	/** Manifest_<ddMMyy>_<N>.xlsx : one row per lot (count = rows of that lot in THIS batch) + a last row for the dump zip. */
	private void writeManifest(CbsBatch batch, String day, String n, String dumpPassword) throws IOException {
		List<String[]> rows = new ArrayList<>();
		rows.add(MANIFEST_HEADER);
		for (CbsLotEntry e : perfiosService.cbsBatchLots(batch.fileName())) {
			java.time.LocalDateTime t = e.at().toLocalDateTime();
			rows.add(new String[] { t.format(CBS_DATE), e.lcpc() + "_" + e.lotNo(), t.format(CBS_DATE_TIME),
					e.lotZipName(), String.valueOf(e.count()), lotZipPassword(e.lotZipName()) });
		}
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		rows.add(new String[] { now.format(CBS_DATE), "CBS Lot Name", now.format(CBS_DATE_TIME),
				batch.fileName(), "", dumpPassword });

		Path dir = Paths.get(cbsManifestDirectoryPath);
		Files.createDirectories(dir);
		Path file = dir.resolve("Manifest_" + day + "_" + n + ".xlsx");
		Path tmp = dir.resolve("Manifest_" + day + "_" + n + ".xlsx.tmp");
		writeXlsx(tmp, rows, 4);   // column E (index 4) = Count, written as a number
		Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		logger.info("Manifest written : {}", file);
	}

	// password of a lot zip = password of the day the zip was made (day is the last part of its name: <lcpc>_LOTn_ddMMyyyy.zip)
	private String lotZipPassword(String lotZipName) {
		Matcher m = LOT_ZIP_DAY.matcher(lotZipName);
		if (m.find()) {
			try {
				return getPassword(LocalDate.parse(m.group(1), DateTimeFormatter.ofPattern("ddMMyyyy")));
			} catch (Exception ignore) { /* fall through */ }
		}
		return getPassword();
	}

	// ---- minimal .xlsx writer (no extra library needed) ----
	private static void writeXlsx(Path file, List<String[]> rows, int numericCol) throws IOException {
		StringBuilder sheet = new StringBuilder(4096);
		sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
			.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
			.append("<cols><col min=\"1\" max=\"6\" width=\"28\" customWidth=\"1\"/></cols><sheetData>");
		for (int i = 0; i < rows.size(); i++) {
			String[] r = rows.get(i);
			sheet.append("<row r=\"").append(i + 1).append("\">");
			for (int j = 0; j < r.length; j++) {
				String v = r[j];
				if (v == null || v.isEmpty()) continue;
				String ref = (char) ('A' + j) + String.valueOf(i + 1);
				if (i > 0 && j == numericCol && v.matches("\\d+")) {
					sheet.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
				} else {
					sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>").append(xmlEscape(v)).append("</t></is></c>");
				}
			}
			sheet.append("</row>");
		}
		sheet.append("</sheetData></worksheet>");

		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
			putXlsxEntry(zos, "[Content_Types].xml",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
				+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
				+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
				+ "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
				+ "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
			putXlsxEntry(zos, "_rels/.rels",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
			putXlsxEntry(zos, "xl/workbook.xml",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
				+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
			putXlsxEntry(zos, "xl/_rels/workbook.xml.rels",
				"<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
			putXlsxEntry(zos, "xl/worksheets/sheet1.xml", sheet.toString());
		}
	}

	private static void putXlsxEntry(ZipOutputStream zos, String name, String content) throws IOException {
		zos.putNextEntry(new ZipEntry(name));
		zos.write(content.getBytes(StandardCharsets.UTF_8));
		zos.closeEntry();
	}

	private static String xmlEscape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private String joinCbs(String[] values) {
		StringBuilder sb = new StringBuilder(512);
		for (int i = 0; i < values.length; i++) {
			if (i > 0) sb.append(CBS_DELIMITER);
			sb.append(csvEscape(cleanCbsValue(values[i])));
		}
		return sb.toString();
	}

	// commas, line breaks and tabs inside a value become a space, so every value stays in ONE column in any tool
	private static String cleanCbsValue(String v) {
		if (v == null) return "";
		String t = v.replaceAll("[\\r\\n\\t]+", " ");
		return CBS_REPLACE_COMMAS ? t.replaceAll("\\s*,\\s*", " ") : t;
	}

	// lock file created atomically (works across servers sharing the folder); stale locks from crashed runs are removed
	private void acquireCbsLock(Path lock) throws IOException {
		long deadline = System.currentTimeMillis() + CBS_LOCK_WAIT_MS;
		while (true) {
			try {
				Files.createFile(lock);
				try {
					Files.writeString(lock, hostId + " " + Instant.now());
				} catch (IOException ignore) { /* informational only */ }
				return;
			} catch (FileAlreadyExistsException e) {
				try {
					long age = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
					if (age > CBS_LOCK_STALE_MS) {
						logger.warn("Removing stale CBS lock {} (age {} ms)", lock, age);
						Files.deleteIfExists(lock);
						continue;
					}
				} catch (NoSuchFileException gone) {
					continue; // released just now, retry immediately
				}
				if (System.currentTimeMillis() > deadline) {
					throw new IOException("Timed out waiting for CBS lock " + lock);
				}
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while waiting for CBS lock", ie);
				}
			}
		}
	}


	// ===================== END CHANGE (CBS-3) =====================

	private String csvEscape(String value) {
		if (value == null) return "";
		
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	private static String getPassword() {
		return getPassword(LocalDate.now());
	}

	// CHANGE (CBS): overload so the CBS zips use the password of their own date
	private static String getPassword(LocalDate now) {
		int month = now.getMonthValue();
		int weekOfMonth = now.get(WeekFields.of(Locale.getDefault()).weekOfMonth());
		int pass = month * 10 + weekOfMonth;
		return "password@" + pass;
	}


	private record CustomerCsvList(String lotFileName, String cif, String accountNumber) { }
	//private record FolderWithTime(Path path, long lastModifiedMillis) { }


	// ===== CHANGE (ZF): new helper - marks the customer as ZF (zip not created) and writes a kyc_perfios_history row =====
	private void markZipFailed(String customerId, String remark) {
		try {
			String[] p = customerId.split("_");   // cif_branch_account
			perfiosService.markZipFailed(p[0], p.length > 2 ? p[2] : "", remark);
		} catch (Exception ex) {
			logger.error("Could not mark ZF for " + customerId, ex);
		}
	}

	// ===== END CHANGE (ZF) =====

	private Path claimFolder(Path sourceFolder, Path processingDir) {
		logger.info("sourceFolder Path : " + sourceFolder);
		String customerId = sourceFolder.getFileName().toString();
		Path target = processingDir.resolve(customerId);
		logger.info("Target Path : " + target);
		try {
			Files.createDirectories(processingDir);
			Path moved = Files.move(sourceFolder, target, StandardCopyOption.ATOMIC_MOVE);
			logger.info("Path moved : "+moved);
			writeClaimMarker(moved);
			return moved;
		} catch (NoSuchFileException e) {
			logger.error("Skipping {} - already claimed or conflicting target exists", customerId);
			logger.error("NoSuchFileException "+e.getMessage());
			// ===== CHANGE (ZF): mark ZF + history on NoSuchFileException =====
			markZipFailed(customerId, "Zip not created - folder not found while claiming");
			// ===== END CHANGE (ZF) =====
			return null;
		} catch (  FileAlreadyExistsException e) {
			logger.error("Skipping {} - already claimed or conflicting target exists", customerId);
			logger.error("FileAlreadyExistsException  "+e.getMessage());
			return null;
		} catch (IOException e) {
			logger.error("Failed to claim folder {}: {}", customerId, e.getMessage(), e);
			e.printStackTrace();
			return null;
		}
	}

	private void writeClaimMarker(Path claimedFolder) {
		Path marker = claimedFolder.resolve(".claim");
		String content = "host=" + hostId + System.lineSeparator() + "claimedAt=" + Instant.now() + System.lineSeparator();
		logger.info("content to write "+content);
		try {
			Files.writeString(marker, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			logger.error("Could not write claim marker for {}: {}", claimedFolder, e.getMessage());
		}
	}

	private Path moveValidatedFolderToClean(Path claimedFolder, Path cleanTargetFolder) {   
		logger.info("Inside move FolderToClean");
		try {
			Files.createDirectories(cleanTargetFolder);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimedFolder)) {
				for (Path file : stream) {
					if (file.getFileName().toString().equals(".claim")) {
						continue; 
					}
					copyForNfs(file, cleanTargetFolder.resolve(file.getFileName()));
				}                                                                
			}
			return cleanTargetFolder;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to push validated folder to clean: " + claimedFolder, e);
		}
	}

	private void copyForNfs(Path source, Path target) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		IOException last = null;
		for (int attempt = 1; attempt <= 3; attempt++) {
			try {
				Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				last = e;
				logger.error("NFS copy attempt {} failed for {}: {}", attempt, source, e.getMessage());
				sleepBackoff(attempt);
			}
		}
		throw last;
	}

	private void sleepBackoff(int attempt) throws IOException {
		try {
			Thread.sleep(200L * attempt);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted during NFS retry backoff", ie);
		}
	}
}
