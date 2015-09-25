package com.verify.main.resolvers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.ds.PGPoolingDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.gson.internal.LinkedTreeMap;
import com.verify.main.util.GsonUtil;
import com.verify.main.util.SSHUtil;
import com.verify.main.util.XpathUtil;
import com.verify.main.verifyobjs.Template;


public class TemplateResolver {
	
	private JdbcTemplate tempalte;
	
	private String SQL_GET_TEMPALTE = "select name_ as name, version_ as version, selectionkey as selectorKey from jbpm_processdefinition left join ttv_selector on id_ = processdefinitionid where processdefinitiontypeid = 2";
	
	private static String JSON_PATH = "/components/BASE";
	
	private static String TEMPLATE_RPM_PATH = "/rpms";
	
	public List<Template> resolveActualTemplate(String ip, String dataBaseName,  String userName, String password){
		
		
		JdbcTemplate tempalte = new JdbcTemplate(getDataSource(ip, dataBaseName, userName, password));
		
		List<Map<String, Object>> templateMap = tempalte.queryForList(SQL_GET_TEMPALTE);
		List<Template> templates = new ArrayList<Template>() ;
		
		for(Map<String, Object> t : templateMap){
			Template template = new Template();
			template.setName((String)t.get("name"));
			template.setVersion(((BigDecimal)t.get("version")).toString());
			template.setSelectorKey((String)t.get("selectorKey"));
			templates.add(template);
		}
		return templates;
		
	}
	
	//TODO Need to figure out the uncertain path and xpath expression
	public List<Template> resolveExpectedTemplate(String rootPath) throws IOException{
		String jsonConfigPath = rootPath + JSON_PATH + "/config.json";
		String templatePath = rootPath + TEMPLATE_RPM_PATH;
		
		Map<String, Object> jsonMap = GsonUtil.convertJsonToMap(jsonConfigPath);
		
		List<LinkedTreeMap> temp = (List)jsonMap.get("selectorKeys");
		Map<String, List<String>> convertedMap = new HashMap<String, List<String>>();
		
		
		for(Map<String, String> map : temp){
			String selectKey = map.get("SELECTIONKEY");
			String template = map.get("TEMPLATE");
			
			if(convertedMap.get(template) == null){
				List<String> selectKeyList = new ArrayList<String>();
				selectKeyList.add(selectKey);
				convertedMap.put(template, selectKeyList);
			}else{
				List<String> selectKeyList = convertedMap.get(template);
				selectKeyList.add(selectKey);
			}
		}
		
		@SuppressWarnings("unchecked")
		List<String> rpmList =  (ArrayList<String>) jsonMap.get("templateRpms");
		
		String unpackRpmCommand = "cd %s; rpm2cpio %s | cpio -div";
		String moveToTmp = "mv %s/opt /tmp";
		String unpackParCommand = "cd /tmp/opt/tandbergtv/cms/workflow/templates/; find . -name \"*.par\" -exec  unzip -n \"{}\" \\;";
		
		SSHUtil util = new SSHUtil();
		util.setHost("localhost");
		util.setUser("cms");
		util.setPassword("1111");
		
		List<Template> expectedList = new ArrayList<Template>();
		
		for(String templateName : rpmList){
			unpackRpmCommand = String.format(unpackRpmCommand, templatePath,  templateName);
			moveToTmp = String.format(moveToTmp, templatePath);
			unpackParCommand = String.format(unpackParCommand, "");
			util.runCommand(unpackRpmCommand);
			util.runCommand(moveToTmp);
			util.runCommand(unpackParCommand);
			
			File parFileDir = new File("/tmp/opt/tandbergtv/cms/workflow/templates/");
			
			String[] jpdlFiles = parFileDir.list(new FilenameFilter(){
				@Override
				public boolean accept(File dir, String name) {
					if(name.endsWith("jpdl")){
						return true;
					}
					return false;
				}
			});
			
			for(String jpdlFile : jpdlFiles){
				Template tempalte = new Template();
				XpathUtil xpathUtil = new XpathUtil("/tmp/opt/tandbergtv/cms/workflow/templates/" + jpdlFile);
				tempalte.setVersion(xpathUtil.getValue("//process-definition/@version").get(0));
				tempalte.setName(xpathUtil.getValue("//process-definition/@name").get(0));
				List<String> selectKey = convertedMap.get(tempalte.getName());
				
				if(selectKey != null){
					Object[] strArray = selectKey.toArray();
					tempalte.setSelectorKey(StringUtils.join(strArray, ","));
				}else{
					tempalte.setSelectorKey("");
				}
				
				
				expectedList.add(tempalte);
			}
			
			File dirToBeDel = new File("/tmp/opt");
			FileUtils.deleteDirectory(dirToBeDel);
		}
		
		return expectedList;
	}
	
	private DataSource getDataSource(String ip, String dataBaseName,  String userName, String password){
		PGPoolingDataSource dataSource = new PGPoolingDataSource();
		dataSource.setServerName(ip);
		dataSource.setDatabaseName(dataBaseName);
		dataSource.setUser(userName);
		dataSource.setPortNumber(5444);
		dataSource.setPassword(password);
		return dataSource;
	}
	
	public JdbcTemplate getTempalte() {
		return tempalte;
	}

	public void setTempalte(JdbcTemplate tempalte) {
		this.tempalte = tempalte;
	}
	
}
