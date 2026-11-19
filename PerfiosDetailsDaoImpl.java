package com.sbi.zip.DAOImpl;

import java.io.File;
import java.nio.file.Files;
import java.security.interfaces.RSAKey;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.sbi.zip.DAO.PerfiosDetailsDAO;
import com.sbi.zip.dto.Customer;
import com.sbi.zip.dto.CustomerCsvRecord;
import com.sbi.zip.dto.SearchBean;
import com.sbi.zip.dto.ZipCustomer;
import com.sbi.zip.configuration.DMSProperties;
import com.sbi.zip.configuration.DatabaseConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import com.sbi.serverConfiguration.ServerConfiguration;
import java.sql.PreparedStatement;   // CHANGE (CBS-2): new import
import java.sql.ResultSetMetaData;   // CHANGE (CBS-2): new import
import java.util.function.Consumer;   // CHANGE (CBS-2): new import
import org.springframework.jdbc.core.ResultSetExtractor;   // CHANGE (CBS-2): new import
import java.util.ArrayList;   // CHANGE (CBS-2): new import
import java.sql.Timestamp;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.CbsBatch;   // CHANGE (CBS-3): new import
import com.sbi.zip.dto.CbsLotEntry;   // CHANGE (CBS-3): new import

@Repository
public class PerfiosDetailsDAOImpl implements PerfiosDetailsDAO {

	private static final JdbcTemplate jdbcTemplate = new JdbcTemplate(DatabaseConfiguration.getDataSource());

	private static final Logger logger = (Logger) LogManager.getLogger(PerfiosDetailsDAO.class);

	private static String sql = DMSProperties.getInstance().getProperty("UPDATE_CUSTOMER_DETAILS");

	public static String outputPath = DMSProperties.getInstance().getProperty("OUTPUT_FOLD");
	public static String cleanPath = DMSProperties.getInstance().getProperty("CLEAN_FOLD");

	@Override
	public
	SearchBean getValuesFromDB(String cifNo) {
		String sql = "SELECT CIF_NUMBER,ACCOUNT_NUMBER,BRANCH_CODE,CIF_STATUS FROM KYCUPDATE.KYC_PERFIOS_CUSTOMER_DETAILS WHERE CIF_NUMBER = ?";

		try {

			List<SearchBean> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
				logger.info("Row found for cif : {}", cifNo);
				SearchBean sb = new SearchBean();
				sb.setCif(rs.getString("CIF_NUMBER"));
				sb.setAccountNumber(rs.getString("ACCOUNT_NUMBER"));
				sb.setBranchCode(rs.getString("BRANCH_CODE"));
				sb.setStatus(rs.getString("CIF_STATUS"));
				return sb;
			}, cifNo);

			return results.stream().findFirst().orElse(new SearchBean());

		} catch (DataAccessException e) {
			logger.error("Database error occurred for CIF: {}", cifNo, e);
			return new SearchBean();
		}
	}

	@Override
	public void updateData(String cif, String cif_status) {
		String formattedCif = padWithZeros(cif, 17);
		//String formattedAcc = padWithZeros(acc, 17);

		try {
			// ckyc_number, branch_code, kyc_update_date, zip_file, lcpc, cif_status, pan, modified_date, cif_pulled_date, zip_created_date, image_uploaded_date, categary, remark
			int cnt = jdbcTemplate.update(sql,cif_status,formattedCif);

			logger.info("Rows Updated : " + cnt);

		} catch (Exception e) {
			logger.error("Error occurred while updating", e);
		}
	}

	private String padWithZeros(String input,int length) {
		if (input == null) input = "";
		StringBuilder sb = new StringBuilder(input);
		while (sb.length() < length) {
			sb.insert(0, "0");
		}
		return sb.toString();
	}

	@Override
	public void updatestatus(SearchBean sb) {

		String statusUpdateSql="Update kyc_perfios_customer_details set cif_status=?,zip_file=?, modified_date=sysdate where cif_number=?";
		logger.info("statusUpdateSql   :  "+statusUpdateSql);
		try {
			int a=jdbcTemplate.update(statusUpdateSql,"ZC",sb.getZipFile(),sb.getCif());
			logger.info("No of rows inserted :  "+a);
		}catch(Exception e) {
			logger.error("Exception  ",e);
		}
	}

	@Override
	public void moveFilesToCleanFolder(String fileName) {
		try {
			File sourceFile = new File(outputPath + File.separator + fileName);
			File destinationFile = new File(cleanPath + File.separator + fileName);

			Files.move(sourceFile.toPath(), destinationFile.toPath());

			logger.info("Files moved successfully");
		}catch(Exception e) {
			logger.error("Error moving Files",e);
		}
	}

	@Override
	public int HistoryInsert(SearchBean sb) {
		logger.info("HistoryInsert started");

		StringUtils.leftPad(sb.getAccountNumber(), 17, "0");
		String sql = DMSProperties.getInstance().getProperty("REKYC_HISTORY_DATA");
		String server = ServerConfiguration.Hostname;

		// INSERT INTO kycupdate.kyc_perfios_history (Cif_number, Account_number, Ckyc_number,  Branch_Code, ZIP_FILE, modified_date,modified_by,Remark,server) values (?,?,?,?,?,sysdate,?,?)]

		try {
			logger.info("History CIF = {}",sb.getCif());
			logger.info("History ACC = {}",sb.getAccountNumber());
			logger.info("History Remark = {}",sb.getRemark());

			int rowsAffected = jdbcTemplate.update(sql, 
					sb.getCif(), 
					sb.getAccountNumber(), 
					"UTIL_4_ZIP_CREATION",
					sb.getRemark(),
					server
					);

			logger.info("HistoryInsert successful. Rows affected: {}", rowsAffected);
			return (rowsAffected > 0) ? rowsAffected : 1;

		} catch (DataAccessException e) {
			logger.error("Database error occurred while inserting history for CIF: {}", sb.getCif(), e);
			return 0; 
		} catch (Exception e) {
			logger.error("Unexpected error occurred during HistoryInsert for CIF: {}", sb.getCif(), e);
			return 0; 
		}
	}

	@Override
	public String checkStatusInPerfiosTable(String cifNumber) {
		String sql = "SELECT CIF_STATUS FROM KYCUPDATE.KYC_PERFIOS_CUSTOMER_DETAILS WHERE CIF_NUMBER = ?";
		try{
			return jdbcTemplate.queryForObject(sql,  String.class,cifNumber);
		}catch(Exception e) {
			logger.error("Unexpected error occurred during checkStatusInPerfiosTable for CIF: "+e.getMessage());
			return "";
		}
	}
	String ckycStatusSet=DMSProperties.getInstance().getProperty("Ckyc.customer.status");
	String kycStatusSet=DMSProperties.getInstance().getProperty("kyc.customer.status");

	@Override
	public boolean checkStatusInMAinTables(String cifNumber) {
		String status="";

		String ckycCustomerQuery="select status from ckyc.ckyc_customer_details where cif_number ='"+ cifNumber + "' "; //null/IP/SB/USB/S/SS/ASB/AUSB
		logger.info("ckycCustomerQuery : "+ckycCustomerQuery);
		try {
			if(jdbcTemplate==null) {
				logger.info("jdbcTemplate is null");
			}
			status=jdbcTemplate.queryForObject(ckycCustomerQuery, String.class);
		}catch(EmptyResultDataAccessException e) {
			logger.error("Exception : ",e.getMessage());
			status="";
		}catch(Exception e) {
			logger.error("Exception : ",e);
			status="";
		}

		List<String> ckycStatusList=Arrays.asList(ckycStatusSet.split(","));

		if(status==null||status.isEmpty()||ckycStatusList.contains(status)){

			List<String> kycStatusList=Arrays.asList(kycStatusSet.split(","));

			String kycCustomerQuery="select status from kycupdate.kyc_customer_details where cif_number ='"+cifNumber+"'";//null/KIP/KSB/KUSB/KS/KSS/S/SS
			logger.info("kycCustomerQuery : "+kycCustomerQuery);
			try {
				status=jdbcTemplate.queryForObject(kycCustomerQuery, String.class);
			}catch(EmptyResultDataAccessException e) {
				logger.error("Exception : ",e.getMessage());
				status="";
			}catch(Exception e) {
				logger.error("Exception : ",e);;
			}
			if(status==null||status.isEmpty()||kycStatusList.contains(status))
				return true;
			else  
				return false;
		}
		return false;
	}

	@Override
	public Object fetchCbsDetail(String cifNumber) {
		return null;
	}

	@Override
	public String fetchOldestLotToProcess() {
		String lotFileFullName="";

		String sql ="""
					select zip_file from(
						select zip_file from kycupdate.kyc_perfios_customer_details where cif_status='ZP' order by modified_date
					)t where rownum=1
					""";

		logger.info("Oldest Lot fetching query: " + sql);
		try{
			lotFileFullName=jdbcTemplate.queryForObject(sql, String.class);
		}catch(Exception e) {
			logger.error("Exception while fetching oldest lot : " + e);
			e.printStackTrace();
		}
		return lotFileFullName;
	}
	
	    
	@Override
	public String fetchNewLotToProcess(String lcpc) {

	/*	String fetchLotSql="""
				 select lot_count from(
				 		select lot_count from kycupdate.Rekyc_perfios_lot_details where lcpc=? order by lot_count desc
				 )t where rownum=1
				""";*/
		
		String fetchLotSql="select max(To_number(trim(lot_count))) from kycupdate.Rekyc_perfios_lot_details where lcpc=?";
		logger.info("fetchLotSql: " + fetchLotSql);
		try {
			return jdbcTemplate.queryForObject(fetchLotSql, String.class,lcpc);
		}catch(Exception e) {
			logger.error("Exception while fetching   lot : " + e);
			return "0";
		}
	}  
	
	
	@Override
	public List<ZipCustomer> fetchCustomerInBatch(String lcpc) {
		String fetchCustomersSql = """
				 SELECT cif_number, branch_code, account_number
				FROM (
				    SELECT cif_number, branch_code, account_number, ROWNUM rn
				    FROM kycupdate.kyc_perfios_customer_details
				    WHERE cif_status = 'ZP' AND lcpc = ?
				)
				WHERE rn < 1000
				""";
		logger.info("fetchLotSql: " + fetchCustomersSql);
		try {
			return jdbcTemplate.query(fetchCustomersSql, new DataClassRowMapper<>(ZipCustomer.class), lcpc);
		}catch(Exception e) {
			logger.info("Exception : "+e);
			return Collections.emptyList();
		}
	}

	
	@Override
	public List<ZipCustomer> fetchLotCustomers(String lotNumber) {
		String sql = "Select cif_number,Branch_code,Account_number "
				+ "from kycupdate.kyc_perfios_customer_details where cif_status = 'ZP' and zip_file like '%"+lotNumber+"%' order by modified_date";
		logger.info("lot fetching query : "+sql);
		try {
			//return jdbcTemplate.query(sql, new DataClassRowMapper<>(ZipCustomer.class),lotNumber);
			return jdbcTemplate.query(sql, new DataClassRowMapper<>(ZipCustomer.class));

		}catch(Exception ex) {
			logger.info("Exception : "+ex);
			return Collections.emptyList();
		}
	}



	public CustomerCsvRecord getEntriesFromDB(String cifNumber,String accountNumber) {

		logger.info("Inside getEntriesFromDB");
		String sql1 ="""
				SELECT CIF_NUMBER, ACCOUNT_NUMBER, DOB, APPLICANT_FIRST_NAME,APPLICANT_MIDDLE_NAME, APPLICANT_LAST_NAME
				FROM KYCUPDATE.KYC_CUSTOMER_DETAILS WHERE CIF_NUMBER = ?
				""";

		logger.info("CustomerSQL: " + sql1);

		try {
			List<Map<String, Object>> customerList = jdbcTemplate.queryForList(sql1,cifNumber);
			String cif = "";
			String account = "";
			String dob = "";
			String firstName = "";
			String middleName = "";
			String lastName = "";

			logger.info("customerList : "+customerList.size());
			SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
			if (!customerList.isEmpty()) {
				Map<String, Object> row = customerList.get(0);

				cif = row.get("CIF_NUMBER") != null ? row.get("CIF_NUMBER").toString():"";
				account = row.get("ACCOUNT_NUMBER") != null ? row.get("ACCOUNT_NUMBER").toString():accountNumber;
				//dob = row.get("DOB") != null ? row.get("DOB").toString():"";
				dob = row.get("DOB") != null ? sdf.format((Date) row.get("DOB")) : "";
				firstName = row.get("APPLICANT_FIRST_NAME") != null ? row.get("APPLICANT_FIRST_NAME").toString():"";
				middleName = row.get("APPLICANT_MIDDLE_NAME") != null ? row.get("APPLICANT_MIDDLE_NAME").toString():"";
				lastName = row.get("APPLICANT_LAST_NAME") != null ? row.get("APPLICANT_LAST_NAME").toString():"";				
				logger.info("Customer: {} {} {}, CIF: {}, Account: {}, DOB: {}",firstName, middleName, lastName, cif, account, dob);
			}

			String sql2 = "SELECT ADDRESS_LINE_1, ADDRESS_LINE_2, ADDRESS_LINE_3 FROM KYCUPDATE.KYC_ADDRESS_DETAILS WHERE FLAG_REGULAR_ADDRESS = 'R' and CIF_NUMBER_FK = ?";
			logger.info("AddressSQL: " + sql2);

			List<Map<String, Object>> addressList = jdbcTemplate.queryForList(sql2,cifNumber);
			logger.info("addressList : "+addressList.size());
			StringBuilder address = new StringBuilder();

			if (!addressList.isEmpty()) {
				Map<String, Object> row = addressList.get(0);
				address.append(row.get("ADDRESS_LINE_1") != null ? row.get("ADDRESS_LINE_1").toString() :"");	
				address.append(row.get("ADDRESS_LINE_2") != null ? row.get("ADDRESS_LINE_2").toString() :"");
				address.append(row.get("ADDRESS_LINE_3") != null ? row.get("ADDRESS_LINE_3").toString() :"");
				logger.info("Complete Address:"+address.toString());
			}
			return new CustomerCsvRecord(cif,account,dob,firstName,middleName,lastName,address.toString());

		} catch (Exception e) {
			logger.error("Database error", e);
			return new CustomerCsvRecord("","","","","","","");
		}
	}
	
	@Override
	public void updateZipStatus(List<CustomerCsvRecord> customerFolders, String lotName) {
		String UpdateSql="update kycupdate.kyc_perfios_customer_details set cif_status='ZC',ZIP_CREATED_DATE = sysdate, REMARK='Zip Created succesfully', PERFIOS_LOTNAME='"+lotName+"' where cif_number=?";
		List<Object[]> batchArgs=customerFolders.stream().map(p->new Object[] {p.cifNumber()}).toList();
		logger.info("No of rows to be updated "+batchArgs.size());
		try{
			int[] a=jdbcTemplate.batchUpdate(UpdateSql,batchArgs);
			logger.info("No of rows updated "+a.length);
		}catch(Exception e) { 
			logger.error("Exception while updating in batch", e);
		}
	}   
	
	@Override
	public void insertHistory(List<CustomerCsvRecord> customerFolders, String lotName) {
		String InsertSql="""
						Insert into kycupdate.kyc_perfios_history (cif_number,Account_number,ZIP_FILE,modified_date,Remark,server,modified_by) 
						values (?,?,?,sysdate,'Zip Created succesfully','','Zip_Creation_Utility')
				""";
		List<Object[]> batchArgs=customerFolders.stream().map(p->new Object[] {p.cifNumber(),p.accountNumber(),lotName,}).toList();
		logger.info("No of rows to be updated "+batchArgs.size());
		try{
			int[] a=jdbcTemplate.batchUpdate(InsertSql,batchArgs);
			logger.info("No of rows updated "+a.length);
		}catch(Exception e) { 
			logger.error("Exception while inserting in batch", e);
		}
	}

	@Override
	public void updateLotHistory(String lotName,String lotNo, String lcpc) {
		String updateLotSql="""
				Insert into kycupdate.Rekyc_perfios_lot_details (Perfios_lotFile,lot_count,lcpc,created_date,status) values (?,?,?,sysdate,?)
				""";
		try {
			jdbcTemplate.update(updateLotSql,lotName,lotNo,lcpc,"Batch created");
		}catch(Exception e) {
			logger.error("Exception while inserting ", e);
		}
	} 

	// ===================== CHANGE (ZF): new method - set status ZF + write kyc_perfios_history row =====================
	@Override
	public void updateZipFailed(String cifNumber, String accountNumber, String remark) {
		String updateSql = "update kycupdate.kyc_perfios_customer_details "
				+ "set cif_status='ZF', modified_date=sysdate, remark=? "
				+ "where cif_number=? and cif_status='ZP'";
		String historySql = "insert into kycupdate.kyc_perfios_history "
				+ "(cif_number, Account_number, ZIP_FILE, modified_date, Remark, server, modified_by) "
				+ "values (?,?,?,sysdate,?,?,'Zip_Creation_Utility')";
		try {
			int rows = jdbcTemplate.update(updateSql, remark, cifNumber);
			logger.info("ZF status set for CIF {} , rows updated {}", cifNumber, rows);
			if (rows > 0) {   // history only when the status really changed from ZP to ZF
				jdbcTemplate.update(historySql, cifNumber, accountNumber, null, remark,
						ServerConfiguration.Hostname);
				logger.info("ZF history inserted for CIF {}", cifNumber);
			}
		} catch (Exception e) {
			logger.error("Exception while setting ZF for CIF " + cifNumber, e);
		}
	}
	// ===================== END CHANGE (ZF) =====================

	// ===================== CHANGE (CBS) - CR 26100024: new method - one row per lot in kyc_cbs_perfios_records =====================
	@Override
	public void insertCbsRecord(java.sql.Date dumpDate, String lotNo, String fileName, String lcpc, int noOfRecords) {
		String sql = "INSERT INTO kycupdate.kyc_cbs_perfios_records "
				+ "(cbs_dump_date, lot_no, file_name, lcpc, no_of_records) VALUES (?,?,?,?,?)";
		jdbcTemplate.update(sql, dumpDate, lotNo, fileName, lcpc, String.valueOf(noOfRecords));
	}
	// ===================== END CHANGE (CBS) =====================

	// ===================== CHANGE (CBS-2) - CBS dump rows for ONE lot (PSO query) =====================
	// Same query the PSO team runs manually. Differences: filters on the lot zip name instead of the last-2-days window,
	// so every lot adds only its own customers. The helper column RN is not written to the file.
	private static final String CBS_DUMP_SQL = """
			SELECT *
			FROM (
			    SELECT
			        a.cif_number as cif_1,
			        a.account_number as acct_no_2,
			        ' ' as ACCT_TYPE_3,
			        ' ' as LOAN_ACCT_FLG_8,
			        ' ' as NO_ACCT_NO_RELTN_9,
			        ' ' as CLSD_ACCT_NO_RELTN_10,
			        ' ' as CUST_TIER_TYPE_CODE_11A,
			        ' ' as CUST_TIER_TYPE_DESC_11B,
			        ' ' as CIF_STATUS_12,
			        ' ' as CIF_DEDUPED_13,
			        ' ' as RISK_CATEGORY_14,
			        ' ' as KYC_SCORE_15,
			        ' ' as KYC_REVIEW_DATE_16,
			        ' ' as CIF_OPEN_DATE_17,
			        ' ' as CIF_CHANNEL_18,
			        ' ' as CIF_DEPOSIT_BAL_19,
			        ' ' as CIF_LOAN_BAL_20,
			        b.APPLICANT_NAME_PREFIX as NAME_PREFIX_CD_21,
			        b.applicant_first_name as FIRST_NAME_22A,
			        b.applicant_middle_name as MIDDLE_NAME_22B,
			        b.applicant_last_name as LAST_NAME_23,
			        ' ' as RELATIVE_CODE_24,
			       b.FATHER_NAME_PREFIX as FATHER_NAME_PREFIX_25A,
			        b.FATHER_FIRST_NAME as FATHER_FIRST_NAME_26A1,
			       b.FATHER_MIDDLE_NAME as FATHER_MID_NAME_26A2,
			     b.FATHER_LAST_NAME  as FATHER_LAST_NAME_27A,
			       b.SPOUSE_NAME_PREFIX as SPOUSE_NAME_PREFIX_25B,
			        b.SPOUSE_FIRST_NAME as SPOUSE_FIRST_NAME_26B1,
			        b.SPOUSE_MIDDLE_NAME as SPOUSE_MID_NAME_26B2,
			        b.SPOUSE_LAST_NAME as SPOUSE_LAST_NAME_27B,
			       b.MOTHER_NAME_PREFIX as MOTHER_NAME_PREFIX_28,
			       b.MOTHER_FIRST_NAME as MOTHER_FIRST_NAME_29A,
			        b.MOTHER_MIDDLE_NAME as MOTHER_MID_NAME_29B,
			        b.MOTHER_LAST_NAME as MOTHER_LAST_NAME_30,
			        b.GENDER as GENDER_31,
			        --b.dob as DATE_OF_BIRTH_32,
			        TO_CHAR(b.dob, 'YYYY-MM-DD') as DATE_OF_BIRTH_32,
			        ' ' as PAN_OR_FORM60_33,
			        (select c.ADDRESS_LINE_1 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_ADDR_LN_1_36,
			        (select c.ADDRESS_LINE_2 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_ADDR_LN_2_37,
			        (select c.ADDRESS_LINE_3 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_ADDR_LN_3_38,
			        (select c.ADDRESS_CITY_VILLAGE from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_CITY_NAME_39C,
			        (select c.district_code_fk from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_DIST_CODE_40A,
			        (select cersai_district from ckyc.pincode_master m where m.cbs_district_id=c.district_code_fk and c.flag_regular_address='R' fetch first row only) as PERM_DESCRIPTION_40B,
			        (select c.state_code_fk from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='R') as PERM_STATE_CODE_41A,
			        (select state_name from ckyc.pincode_master m where m.state_code=c.STATE_CODE_FK and c.flag_regular_address='R' fetch first row only) as PERM_STATE_NAME_41B,
			        ' ' as PERM_COUNTRY_CODE_42A,
			        ' ' as PERM_COUNTRY_NAME_42B,
			         case when c.address_pin_code = 'Other' and c.flag_regular_address='R' then c.other_pincode when c.address_pin_code <> 'Other' and c.flag_regular_address='R' then c.address_pin_code end as PERM_ADDR_PINCODE_43,
			        (select c.ADDRESS_LINE_1 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.FLAG_CORRESPONDENCE='CO') as CURRENT_ADDR_LN_1_45,
			        (select c.ADDRESS_LINE_2 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.FLAG_CORRESPONDENCE='CO') as CURRENT_ADDR_LN_2_46,
			        (select c.ADDRESS_LINE_3 from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.FLAG_CORRESPONDENCE='CO') as CURRENT_ADDR_LN_3_47,
			        (select c.ADDRESS_CITY_VILLAGE from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='CO') as CURRENT_ADDR_LN_4_48A,
			        (select c.district_code_fk from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='CO') as CURRENT_CITY_CODE_48B,
			        (select cersai_district from ckyc.pincode_master m where m.cbs_district_id=c.district_code_fk and c.flag_regular_address='CO' fetch first row only) as CURRENT_CITY_NAME_48C,
			        (select c.state_code_fk from kycupdate.kyc_address_details c where c.cif_number_fk=a.cif_number and c.flag_regular_address='CO') as CURRENT_STATE_CODE_50A,
			        (select state_name from ckyc.pincode_master m where m.state_code=c.STATE_CODE_FK and c.flag_regular_address='CO' fetch first row only) as CURRENT_STATE_NAME_50B,
			        ' ' as CURRENT_COUNTRY_CODE_51A,
			        ' ' as CURRENT_COUNTRY_NAME_51B,
			        case when c.address_pin_code = 'Other' and c.flag_regular_address='CO' then c.other_pincode when c.address_pin_code <> 'Other' and c.flag_regular_address='CO' then c.address_pin_code end as CURRENT_ADDR_PINCODE_52,
			        ' ' as ID1_NBR_53,
			        ' ' as ID1_TYP_54,
			        ' ' as ID1_DESCRIPTION_55,
			        ' ' as ID2_NBR_56,
			        ' ' as ID2_TYP_57,
			        ' ' as ID2_DESCRIPTION_58,
			        ' ' as ID3_NBR_59,
			        ' ' as ID3_TYP_60,
			        ' ' as ID3_DESCRIPTION_61,
			        ' ' as AADHAR_SEEDED_FLG_62,
			        ' ' as CUST_TAX_PAN_63,
			        ' ' as FR60_FLG_64,
			        ' ' as CKYC_NO_65,
			        a.branch_code as BRANCHCD_66A,
			        ' ' as BRANCHNAME_66B,
			        ' ' as REGIONNAME_67,
			        ' ' as MODULENAME_68,
			        ' ' as NETWORKNAME_69,
			        ' ' as circlecd_70a,
			        ' ' as circlename_70b,
			        ' ' as FATHER_NAME,
			        ' ' as MOTHER_NAME,
			        ' ' as MARITAL_STATUS,
			        ' ' as ADD_PROOF_CKYC,
			        ROW_NUMBER() OVER (PARTITION BY a.cif_number ORDER BY a.account_number) as rn
			    FROM
			        kycupdate.kyc_perfios_customer_details a
			    INNER JOIN kycupdate.kyc_customer_details b ON a.cif_number = b.cif_number
			    INNER JOIN kycupdate.kyc_address_details  c ON a.cif_number = c.cif_number_fk
			    WHERE
			        a.perfios_lotname = ?
			        and a.cif_status='ZC'
			)
			WHERE rn = 1
			""";

	@Override
	public void streamCbsDump(String lotName, Consumer<String[]> onHeader, Consumer<String[]> onRow) {
		jdbcTemplate.query(con -> {
			PreparedStatement ps = con.prepareStatement(CBS_DUMP_SQL);
			ps.setFetchSize(1000);
			ps.setString(1, lotName);
			return ps;
		}, (ResultSetExtractor<Void>) rs -> {
			ResultSetMetaData md = rs.getMetaData();
			List<Integer> cols = new ArrayList<>();
			for (int i = 1; i <= md.getColumnCount(); i++) {
				if (!"RN".equalsIgnoreCase(md.getColumnLabel(i))) cols.add(i);
			}
			String[] header = new String[cols.size()];
			for (int i = 0; i < cols.size(); i++) header[i] = md.getColumnLabel(cols.get(i));
			onHeader.accept(header);
			while (rs.next()) {
				String[] row = new String[cols.size()];
				for (int i = 0; i < cols.size(); i++) {
					String v = rs.getString(cols.get(i));
					row[i] = (v == null) ? "" : v;
				}
				onRow.accept(row);
			}
			return null;
		});
	}
	// ===================== END CHANGE (CBS-2) =====================

	// ===================== CHANGE (CBS-3) - batch wise CBS dump: ledger methods on kyc_cbs_perfios_records =====================
	// New columns needed on the table: BATCH_NO NUMBER(10), BATCH_STATUS VARCHAR2(10), LOT_ZIP_NAME VARCHAR2(100)
	// One row = one lot (or the part of a lot) inside one batch. file_name = the batch dump zip name.

	// the batch that is still filling (status OPEN) with the number of records it already holds
	@Override
	public CbsBatch findOpenCbsBatch() {
		String sql = "select file_name, batch_no, sum(to_number(no_of_records)) as cnt "
				+ "from kycupdate.kyc_cbs_perfios_records where batch_status = 'OPEN' "
				+ "group by file_name, batch_no order by min(cbs_dump_date), batch_no fetch first 1 row only";
		List<CbsBatch> l = jdbcTemplate.query(sql, (rs, i) -> new CbsBatch(rs.getString(1), rs.getInt(2), rs.getLong(3)));
		return l.isEmpty() ? null : l.get(0);
	}

	// next batch number for a date (ddMMyy): restarts at 1 every day
	@Override
	public int nextCbsBatchNo(String ddMMyy) {
		String sql = "select nvl(max(batch_no),0)+1 from kycupdate.kyc_cbs_perfios_records where file_name like ? escape '\\'";
		Integer n = jdbcTemplate.queryForObject(sql, Integer.class, "CBS\\_DUMP\\_" + ddMMyy + "\\_%");
		return n == null ? 1 : n;
	}

	// records of this lot zip already saved in any batch (used to resume a lot that stopped half way)
	@Override
	public int cbsRecordsDoneForLot(String lotZipName) {
		String sql = "select nvl(sum(to_number(no_of_records)),0) from kycupdate.kyc_cbs_perfios_records where lot_zip_name = ?";
		Integer n = jdbcTemplate.queryForObject(sql, Integer.class, lotZipName);
		return n == null ? 0 : n;
	}

	@Override
	public void insertCbsBatchRecord(Timestamp at, String lotNo, String batchFile, String lcpc,
			int count, int batchNo, String lotZipName) {
		String sql = "insert into kycupdate.kyc_cbs_perfios_records "
				+ "(cbs_dump_date, lot_no, file_name, lcpc, no_of_records, batch_no, batch_status, lot_zip_name) "
				+ "values (?,?,?,?,?,?,'OPEN',?)";
		jdbcTemplate.update(sql, at, lotNo, batchFile, lcpc, String.valueOf(count), batchNo, lotZipName);
	}

	@Override
	public void closeCbsBatch(String batchFile) {
		jdbcTemplate.update("update kycupdate.kyc_cbs_perfios_records set batch_status = 'CLOSED' where file_name = ?", batchFile);
	}

	// lots (or parts of lots) of one batch, in the order they were added - used for the manifest
	@Override
	public List<CbsLotEntry> cbsBatchLots(String batchFile) {
		String sql = "select cbs_dump_date, lcpc, lot_no, lot_zip_name, to_number(no_of_records) "
				+ "from kycupdate.kyc_cbs_perfios_records where file_name = ? order by cbs_dump_date, lot_zip_name";
		return jdbcTemplate.query(sql, (rs, i) -> new CbsLotEntry(rs.getTimestamp(1), rs.getString(2),
				rs.getString(3), rs.getString(4), rs.getInt(5)), batchFile);
	}
	// ===================== END CHANGE (CBS-3) =====================



}

/*
record CustomerData(String cif, String account, String dob, 
		String firstName, String middleName, String lastName) {}
// 2. Define the RowMapper (Lambda)
RowMapper<CustomerData> mapper = (rs, rowNum) -> new CustomerData(
		rs.getString("CIF_NUMBER"),
		rs.getString("ACCOUNT_NUMBER"),
		rs.getString("DOB"),
		rs.getString("APPLICANT_FIRST_NAME"),
		rs.getString("APPLICANT_MIDDLE_NAME"),
		rs.getString("APPLICANT_LAST_NAME")
		);

// 3. Execute Query (Note the argument order: SQL, Mapper, Args)
String sql = "SELECT CIF_NUMBER, ACCOUNT_NUMBER, DOB, APPLICANT_FIRST_NAME, APPLICANT_MIDDLE_NAME, APPLICANT_LAST_NAME FROM customers WHERE cif_number = ?";

try {
	// Correct signature: queryForObject(String sql, RowMapper<T> rowMapper, Object... args)
	CustomerData customer = jdbcTemplate.queryForObject(sql, mapper, cifNumber);

	logger.info("Customer: {} {} {}, CIF: {}, Account: {}, DOB: {}", 
			customer.firstName(), customer.middleName(), customer.lastName(), 
			customer.cif(), customer.account(), customer.dob());

} catch (Exception e) {
	// Handle case where no rows are found
	logger.warn("No customer found for CIF: {}", cifNumber);
}


String sql = "SELECT CIF_NUMBER, ACCOUNT_NUMBER, DOB, APPLICANT_FIRST_NAME, APPLICANT_MIDDLE_NAME, APPLICANT_LAST_NAME FROM customers WHERE cif_number = ?";

Optional<CustomerData> customerOpt = jdbcTemplate.query(sql, new Object[]{cifNumber}, (rs, rowNum) -> {
    return new CustomerData(
        rs.getString("CIF_NUMBER"),
        rs.getString("ACCOUNT_NUMBER"),
        rs.getString("DOB"),
        rs.getString("APPLICANT_FIRST_NAME"),
        rs.getString("APPLICANT_MIDDLE_NAME"),
        rs.getString("APPLICANT_LAST_NAME")
    );
}).stream().findFirst();

*/
