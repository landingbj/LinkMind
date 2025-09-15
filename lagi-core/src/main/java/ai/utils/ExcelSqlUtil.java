package ai.utils;

import ai.config.ContextLoader;
import ai.database.impl.MysqlAdapter;
import ai.database.impl.SqliteAdapter;
import ai.database.pojo.SQLJdbc;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.json.JSONObject;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.google.common.collect.Lists;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExcelSqlUtil {
    private static SQLJdbc sqlJdbc ;
    private static MysqlAdapter mysqlAdapter;
    private static SqliteAdapter sqliteAdapter;
    private static final Logger log = LoggerFactory.getLogger(ExcelSqlUtil.class);
    private static boolean isSwitch = false;

    static {
        //        ContextLoader.loadContext();
        List<SQLJdbc> database = ContextLoader.configuration.getStores().getDatabase();
        if(!(database== null || database.isEmpty())) {
            sqlJdbc = ContextLoader.configuration.getStores().getDatabase().stream()
                    .findFirst()  // 获取流中的第一个元素
                    .orElseThrow(() -> new NoSuchElementException("No database found"));
            try {
                mysqlAdapter = new MysqlAdapter(sqlJdbc.getName());
                if (isConnect()){
                    if (new MysqlAdapter(sqlJdbc.getName()).selectCount("SELECT 1")>0){
                        isSwitch =initTextToSqlSearch();
                        if (!isSwitch){
                            log.info("mysql初始化失败！---智能问数模式已关闭！");
                        }
                    }
                }else {
                    sqliteAdapter = new SqliteAdapter();
                }
            }catch (Exception e){
                log.error("初始化失败,智能问数模式已关闭！");
            }
        }
    }

    public static boolean isConnect(){
        if (mysqlAdapter!=null&&isSwitch){
            return mysqlAdapter.selectCount("SELECT 1")>0;
        }
        return false;
    }

    public static boolean isSqlietConnect(){
        if (sqliteAdapter!=null){
            return sqliteAdapter.selectCount("SELECT 1")>0;
        }
        return false;
    }

    public static boolean isSql(String excelFilePath) {
        File file = new File(excelFilePath);
        boolean flag = false;
        if (excelFilePath.endsWith(".csv")){
            String line = "";
            String csvSplitBy = ",";
            int totalCells = 0;
            int numericCells = 0;

            List<Charset> possibleCharsets = Arrays.asList(Charset.forName("GBK"), Charset.forName("ISO-8859-1"));
            for (Charset charset : possibleCharsets) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset))) {
                    while ((line = br.readLine()) != null) {
                        String[] cells = line.split(csvSplitBy);
                        totalCells += cells.length;
                        for (String cell : cells) {
                            if (isNumeric(cell)) {
                                numericCells++;
                            }
                        }
                    }
                    double percentage = ((double) numericCells / totalCells) * 100;
                    flag = totalCells>0? (percentage > 50):flag;
                    System.out.printf("Percentage of numeric data in CSV: %.2f%%\n", percentage);
                    break;
                } catch (IOException e) {
                    System.out.println(e);
                    return false;
                }
            }
            return flag;
        }
        ExcelReader reader = ExcelUtil.getReader(file);
        List<String> sheetNames = reader.getSheetNames();

        for (int i = 0; i < sheetNames.size(); i++) {
            try {
                FileInputStream fis = new FileInputStream(file);
                Workbook workbook = null;
                if (excelFilePath.endsWith(".xlsx")) {
                    workbook = new XSSFWorkbook(fis);
                } else if (excelFilePath.endsWith(".xls")) {
                    POIFSFileSystem poifs = new POIFSFileSystem(fis);
                    workbook = new HSSFWorkbook(poifs);
                } else {
                    return false;
                }
                FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
                Sheet sheet = workbook.getSheetAt(i);
                int totalCells = 0;
                int numberCells = 0;
                Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        totalCells++;
                        String cellValue = getCellValue(cell,formulaEvaluator);
                        Matcher matcher = pattern.matcher(cellValue);
                        if (matcher.matches()) {
                            numberCells++;
                        }
                    }
                }
                double percentage = (totalCells == 0) ? 0 : (double) numberCells / totalCells * 100;
                System.out.println("数字类型数据的百分比: " + percentage + "%");
                workbook.close();
                fis.close();
                flag = totalCells>0? (percentage > 40):flag;
            }catch (Exception e){
                System.out.println(e);
                return false;
            }
        }
        return flag;
    }
    private static String getCellValue(Cell cell, FormulaEvaluator formulaEvaluator) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                switch (formulaEvaluator.evaluateInCell(cell).getCellType()) {
                    case NUMERIC:
                        return String.valueOf(cell.getNumericCellValue());
                    case STRING:
                        return cell.getStringCellValue();
                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    default:
                        return "";
                }
            default:
                return "";
        }
    }
    public static void uploadSql(String filePath,String fileName,String fileId) throws IOException  {
        File file = new File(filePath);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }
        if (file.getName().endsWith(".csv")){
            Map<String, List<List<String>>> res = EasyExcelUtil.readCsv(filePath);
            List<List<String>> result =  res.get("data");
            List<String> headers =  res.get("header").get(0);
            StringBuilder description = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                String fieldName = "字段field" + (i + 1);
                description.append(fieldName + " 代表 " + headers.get(i) + ",\n");
            }
            String insertSQL = "INSERT INTO table_info (table_name,file_id,description) VALUES ('%s','%s','%s');";
            insertSQL = String.format(insertSQL, fileName,fileId, description);
            Integer table_info_id = 0;
            if (isConnect()){
                table_info_id = mysqlAdapter.executeUpdateGeneratedKeys(insertSQL);
            }else {
                table_info_id = sqliteAdapter.executeUpdateGeneratedKeys(insertSQL);
            }


            Integer fieldSize = headers.size();
            StringBuilder fields = new StringBuilder("table_info_id");
            for (int i = 1; i <= fieldSize; i++) {
                fields.append(", field").append(i);
            }
            StringBuilder sql = new StringBuilder("INSERT INTO detailed_data (" + fields + ") VALUES ");
            for (int i = 0; i < result.size(); i++) {
                List<String> dataRow = result.get(i);
                sql.append("(").append(table_info_id);

                for (Integer j = 0; j < fieldSize; j++) {
                    String value = dataRow.size()>j ? dataRow.get(j).toString() : null;
                    if (value == null) {
                        sql.append(", NULL");
                    } else {
                        String cleanValue = value.replace("'", "''");
                        sql.append(", '").append(cleanValue.trim()).append("'");
                    }
                }

                if (i < result.size() - 1) {
                    sql.append("), ");
                } else {
                    sql.append(");");
                }
            }
            if (isConnect()){
                mysqlAdapter.executeUpdate(sql.toString());
            }else {
                sqliteAdapter.executeUpdate(sql.toString());
            }
            return;
        }

        ExcelReader reader = ExcelUtil.getReader(file);
        List<String> sheetNames = reader.getSheetNames();
        Map<String, List<List<JSONObject>>> list = new HashMap<>();
        for (int i = 0; i < sheetNames.size(); i++) {
            List<List<JSONObject>> result = EasyExcelUtil.readMergeExcel(filePath, i, 0, 0);
            if (result.size() > 0){
                list.put(fileName+"里的"+sheetNames.get(i)+"表", result);
            }
        }
        for (Map.Entry<String, List<List<JSONObject>>> entry : list.entrySet()) {
            String tableName = entry.getKey();
            List<List<JSONObject>> rows = entry.getValue();

            List<JSONObject> headerRow = rows.get(0);
            List<String> headers = new ArrayList<>();
            for (JSONObject cell : headerRow) {
                headers.add(cell.getStr("cellValue"));
            }
            StringBuilder description = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                String fieldName = "字段field" + (i + 1);
                description.append(fieldName + " 代表 " + headers.get(i) + ",\n");
            }
            String insertSQL = "INSERT INTO table_info (table_name,file_id,description) VALUES ('%s','%s','%s');";
            insertSQL = String.format(insertSQL, tableName,fileId, description);
            Integer table_info_id = 0;
            if (isConnect()){
                table_info_id = mysqlAdapter.executeUpdateGeneratedKeys(insertSQL);
            }else {
                table_info_id = sqliteAdapter.executeUpdateGeneratedKeys(insertSQL);
            }
            setDetailsTable(headers.size(), rows, table_info_id);
        }
    }
    public static boolean deleteSql(String fileId) {
        String sql = "DELETE FROM table_info WHERE file_id = ?";
        boolean ismysql = false;
        if (isConnect()){
            ismysql = mysqlAdapter.executeUpdate(sql, fileId)>0;
        }else {
            ismysql = sqliteAdapter.executeUpdate(sql, fileId)>0;
        }
        return ismysql;
    }
    public static boolean deleteListSql(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return false;
        }
        String idListStr = idList.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));
        String sql = "DELETE FROM table_info WHERE file_id IN (" + idListStr + ");";
        boolean ismysql = false;
        if (isConnect()){
            ismysql = mysqlAdapter.executeUpdate(sql)>0;
        }else {
            ismysql = sqliteAdapter.executeUpdate(sql)>0;
        }
        return ismysql;
    }
    public static boolean truncationSql() {
        String sql = "DELETE d FROM detailed_data d JOIN table_info t ON d.table_info_id = t.id;";
        boolean ismysql = false;
        if (isConnect()){
            ismysql = mysqlAdapter.executeUpdate(sql)>0;
        }else {
            ismysql = sqliteAdapter.executeUpdate(sql)>0;
        }
        return ismysql;
    }
    private static void setDetailsTable(Integer fieldSize, List<List<JSONObject>> rows,Integer table_info_id) {

        StringBuilder fields = new StringBuilder("table_info_id");
        for (int i = 1; i <= fieldSize; i++) {
            fields.append(", field").append(i);
        }
        final int BATCH_SIZE = 1000; // 每批次插入1000条数据
        for (int batchStart = 1; batchStart < rows.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, rows.size());
            StringBuilder sql = new StringBuilder("INSERT INTO detailed_data (" + fields + ") VALUES ");

            for (int i = batchStart; i < batchEnd; i++) {
                List<JSONObject> dataRow = rows.get(i);
                List<String> rowData = new ArrayList<>();
                String text = "";
                for (int j = 0; j < dataRow.size(); j++) {
                    JSONObject cell = dataRow.get(j);
                    String cellValue = cell.getStr("cellValue");

                    if (cell.containsKey("isMerge") && cell.getBool("isMerge")) {
                        int firstRow = cell.getInt("firstRow");
                        int firstCol = cell.getInt("firstColumn");
                        if (firstRow == i && firstCol == j) {
                            rowData.add(cellValue);
                        } else {
                            rowData.add("");
                        }
                    } else {
                        rowData.add(cellValue);
                    }
                }

                sql.append("(").append(table_info_id);

                for (Integer j = 0; j < fieldSize; j++) {
                    String value = rowData.size() > j ? rowData.get(j).toString() : null;
                    if (value == null) {
                        sql.append(", NULL");
                    } else {
                        String cleanValue = value.replace("'", "''");
                        sql.append(", '").append(cleanValue.trim()).append("'");
                    }
                }

                if (i < batchEnd - 1) {
                    sql.append("), ");
                } else {
                    sql.append(");");
                }
            }

            boolean ismysql = false;
            if (isConnect()) {
                ismysql = mysqlAdapter.executeUpdate(sql.toString()) <= 0;
            } else {
                ismysql = sqliteAdapter.executeUpdate(sql.toString()) <= 0;
            }
            if (ismysql) {
                throw new RuntimeException("插入数据失败");
            }
        }
    }
    public static String getDetails() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (isConnect()){
            list = new MysqlAdapter("mysql").sqlToValue("SELECT * FROM table_info;");
        }else {
            list = sqliteAdapter.sqlToValue("SELECT * FROM table_info;");
        }
        if (list.size()<=0){
            return null;
        }
        return toIntroduce(list);
    }
    private static String toIntroduce(List<Map<String, Object>> list) {
        String intentDetection = "角色：数据分析师，MYSQL专家。任务：编写高效SQL。\n" +
                "表结构：表 table_info 用于存储有关其他数据库表的信息。\n" +
                "它包含以下字段：id: 整数类型，自动递增，作为唯一标识符，\n" +
                "用于识别每条记录。table_name: 字符串类型，不可为空，\n" +
                "用于存储数据库表的名称。description: 文本类型，可选，\n" +
                "用于存储关于表的详细描述。SQL格式：以#开头和结尾，模版：\n" +
                "#SELECT id, table_name, description FROM table_info WHERE id IN (相关表ID列表);#\n" +
                "如#SELECT * FROM table_info WHERE id IN (1) ;#\n" +
                "相关表ID：" +
                formatDescription(list) +
                "要求：只用告诉我有关的表名和其对应表table_info的id，table_name，description有那些，" +
                "回答内容在100字以内，给出可以查出和我需求有关的表的详细信息的sql。\n";
        return intentDetection;

    }
    private static String formatDescription(List<Map<String, Object>> list) {
        StringBuilder formattedDescription = new StringBuilder("\n------\n");
        for (Map<String, Object> map : list) {
            int id = (int) map.get("id");
            String tableName = (String) map.get("table_name");
            formattedDescription.append(tableName)
                    .append("，对应id为:").append(id+"\n");
        }

        return formattedDescription.toString();
    }
    public static String extractContentWithinBraces(String input) {
        if (input == null || !input.contains("#") || !input.contains("#")) {
            return input;
        }
        input = input.replace("\n", " ");
        int firstHashIndex = input.indexOf("#");
        int lastHashIndex = input.lastIndexOf("#");
        if (firstHashIndex == lastHashIndex) {
            return input;
        }

        String betweenHashes = input.substring(firstHashIndex + 1, lastHashIndex);
        betweenHashes = betweenHashes.replace("#", " ");
        int semicolonIndex = betweenHashes.indexOf(";");

        if (semicolonIndex != -1) {
            betweenHashes = betweenHashes.substring(0, semicolonIndex);
        }
        return betweenHashes.replace("#", "");
    }
    public static String toText(String sql,String demand,String sql1) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (isConnect()){
            list = mysqlAdapter.sqlToValue(sql);
        }else {
            list = sqliteAdapter.sqlToValue(sql);
        }
        List<Map<String, Object>> list1 = new ArrayList<>();
        if (isConnect()){
            list1 = mysqlAdapter.sqlToValue(sql1);
        }else {
            list1 = sqliteAdapter.sqlToValue(sql1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("表名为：detailed_data，");
        sb.append("字段table_info_id，int(11)，");
        sb.append("field1 到 field36，varchar(512)");
        for (Map<String, Object> map : list1) {
            sb.append("table_name=“"+map.get("table_name")+"”"+"table_info_id为："+map.get("id")+"\n，"+"表详细信息："+map.get("description"));
        }
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        chatCompletionRequest.setCategory("default");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("现在你是一名报表分析师，请仅根据用户提供的markdown表的信息，" +
                "用户的需求，以及sql查询数据库返回的信息，给客户一个满意的回答." +
                "表信息如下：\n "+
                sb.toString()+
                "结合需求通过sql：" +sql+
                "查数据库返回的信息的信息是：“ \n "+convertToMarkdown(list)+
                " \n ”。用户需求:“" + demand+"”。" );
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        System.out.println(message.getContent());
        chatCompletionRequest.setModel(mysqlAdapter.model);
        CompletionsService completionsService = new CompletionsService();
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        String out = null;
        if(result != null){
            out = result.getChoices().get(0).getMessage().getContent();
        }
        return out;
    }
    public static String convertToMarkdown(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        Set<String> columns = list.get(0).keySet();
        StringBuilder markdown = new StringBuilder();
        markdown.append("| ");
        for (String column : columns) {
            markdown.append(column).append(" | ");
        }
        markdown.append("\n");
        markdown.append("|");
        for (int i = 0; i < columns.size(); i++) {
            markdown.append(" ------- |");
        }
        markdown.append("\n");

        for (Map<String, Object> map : list) {
            markdown.append("| ");
            for (String column : columns) {
                markdown.append(map.get(column)).append(" | ");
            }
            markdown.append("\n");
        }

        return markdown.toString();
    }
    public static String WorkflowsToSql(String demand) {
        String details = getDetails();
        if (details==null){
            return null;
        }
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        chatCompletionRequest.setCategory("default");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent( getDetails() + "用户需求:" + demand );
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setModel(mysqlAdapter.model);
        CompletionsService completionsService = new CompletionsService();

        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        String out = null;
        if (result != null) {
            out = result.getChoices().get(0).getMessage().getContent();
        }
        return extractContentWithinBraces(out);
    }
    public static String toSql(String sql,String demand) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (isConnect()){
            list = mysqlAdapter.sqlToValue(sql);
        }else {
            list = sqliteAdapter.sqlToValue(sql);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("表名为：detailed_data，");
        sb.append("字段table_info_id，int(11)，");
        sb.append("field1 到 field36，varchar(512)");
        for (Map<String, Object> map : list) {
            sb.append("table_name=“"+map.get("table_name")+"”"+"table_info_id为："+map.get("id")+"\n，"+"表详细信息："+map.get("description"));
        }
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        chatCompletionRequest.setCategory("default");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("现在你是一个数据分析师,MYSQL大神,请根据用户提供的表的信息，以及用户的需求，写出效率最高的SQL," +
                "当涉及到中文字段的时候你比较喜欢使用模糊查询." +
                "表信息如下："+
                sb.toString()+
                "输并且要求输出的S0L以#开头,以#结尾，样例如下:" +
                "#SELECT id, table_name, description FROM table_info WHERE id IN (相关表ID列表);#\n" +
                "如#SELECT * FROM table_info WHERE id IN (1,2) AND field1 LIKE '%全%' ;#\n" +
                "注意不需要分析过程，" +
                "用户需求:" + demand
        );
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setModel(mysqlAdapter.model);
        CompletionsService completionsService = new CompletionsService();

        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        String out = null;
        if (result != null) {
            out = result.getChoices().get(0).getMessage().getContent();
        }
        return extractContentWithinBraces(out);
    }
    public static boolean isNumeric(String str) {
        if (str == null) {
            return false;
        }
        try {
            Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean initTextToSqlSearch(){
        Connection conn = null;
        Statement stmt = null;
        try {
            Class.forName(sqlJdbc.getDriver());
            conn = DriverManager.getConnection(sqlJdbc.getJdbcUrl(), sqlJdbc.getUsername(), sqlJdbc.getPassword());
            stmt = conn.createStatement();
            DatabaseMetaData dbm = conn.getMetaData();
            ResultSet tables = dbm.getTables(null, null, "table_info", null);
            if (!tables.next()) {
                String createTableInfo = "CREATE TABLE table_info (" +
                        " id INT AUTO_INCREMENT PRIMARY KEY COMMENT '唯一标识符，自动递增'," +
                        " file_id VARCHAR(256) NOT NULL COMMENT '文件id'," +
                        " table_name VARCHAR(512) NOT NULL COMMENT '表的名称'," +
                        " description VARCHAR(512) COMMENT '表的详细介绍'" +
                        ") COMMENT '存储表信息的通用表'";
                stmt.executeUpdate(createTableInfo);
            }

            tables = dbm.getTables(null, null, "detailed_data", null);
            if (!tables.next()) {
                String createDetailedData = "CREATE TABLE detailed_data (" +
                        " id INT AUTO_INCREMENT PRIMARY KEY COMMENT '唯一标识符，自动递增'," +
                        " table_info_id INT NOT NULL COMMENT '关联table_info表的id'," +
                        " field1 VARCHAR(512) COMMENT '字段1'," +
                        " field2 VARCHAR(512) COMMENT '字段2'," +
                        " field3 VARCHAR(512) COMMENT '字段3'," +
                        " field4 VARCHAR(512) COMMENT '字段4'," +
                        " field5 VARCHAR(512) COMMENT '字段5'," +
                        " field6 VARCHAR(512) COMMENT '字段6'," +
                        " field7 VARCHAR(512) COMMENT '字段7'," +
                        " field8 VARCHAR(512) COMMENT '字段8'," +
                        " field9 VARCHAR(512) COMMENT '字段9'," +
                        " field10 VARCHAR(512) COMMENT '字段10'," +
                        " field11 VARCHAR(512) COMMENT '字段11'," +
                        " field12 VARCHAR(512) COMMENT '字段12'," +
                        " field13 VARCHAR(512) COMMENT '字段13'," +
                        " field14 VARCHAR(512) COMMENT '字段14'," +
                        " field15 VARCHAR(512) COMMENT '字段15'," +
                        " field16 VARCHAR(512) COMMENT '字段16'," +
                        " field17 VARCHAR(512) COMMENT '字段17'," +
                        " field18 VARCHAR(512) COMMENT '字段18'," +
                        " field19 VARCHAR(512) COMMENT '字段19'," +
                        " field20 VARCHAR(512) COMMENT '字段20'," +
                        " field21 VARCHAR(512) COMMENT '字段21'," +
                        " field22 VARCHAR(512) COMMENT '字段22'," +
                        " field23 VARCHAR(512) COMMENT '字段23'," +
                        " field24 VARCHAR(512) COMMENT '字段24'," +
                        " field25 VARCHAR(512) COMMENT '字段25'," +
                        " field26 VARCHAR(512) COMMENT '字段26'," +
                        " field27 VARCHAR(512) COMMENT '字段27'," +
                        " field28 VARCHAR(512) COMMENT '字段28'," +
                        " field29 VARCHAR(512) COMMENT '字段29'," +
                        " field30 VARCHAR(512) COMMENT '字段30'," +
                        " field31 VARCHAR(512) COMMENT '字段31'," +
                        " field32 VARCHAR(512) COMMENT '字段32'," +
                        " field33 VARCHAR(512) COMMENT '字段33'," +
                        " field34 VARCHAR(512) COMMENT '字段34'," +
                        " field35 VARCHAR(512) COMMENT '字段35'," +
                        " field36 VARCHAR(512) COMMENT '字段36'," +
                        " FOREIGN KEY (table_info_id) REFERENCES table_info(id) ON DELETE CASCADE" +
                        ") COMMENT='存储详细数据的表';";
                stmt.executeUpdate(createDetailedData);
            }
            log.error("Tables created successfully or already exist.");
            return true;
        } catch (ClassNotFoundException e) {
            log.error("JDBC Driver not found.");
            return false;
        } catch (SQLException e) {
            log.error("An error occurred while checking or creating tables.");
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void text(){
        String demand = "高三三班学生各科成绩都大于60分的人有那些，总分分别是多少。";
        String sql1 = WorkflowsToSql(demand);
//        System.out.println("生成的sql是:\n"+sql1);
        String sql = toSql(sql1,demand);
//        System.out.println("生成的sql是:\n"+sql);
        String out = toText(sql,demand, sql1);
        System.out.println(out);
    }

    @Test
    public void text1(){
        SqliteAdapter mysqlAdapter = new SqliteAdapter();
        List<Map<String, Object>> list = new ArrayList<>();
        //SELECT id, table_name, description FROM table_info
        list = mysqlAdapter.sqlToValue("SELECT id, table_name, description FROM table_info");
        System.out.println(list);
    }

    @Test
    public void text2(){

        System.out.println(isConnect());
    }


    public static void main(String[] args) {
        try {
            String excelFilePath = "C:\\Users\\ruiqing.luo\\Desktop\\rag调优\\节点分类-测试用.csv";
            System.out.println("是否走sql:"+isSql(excelFilePath));
            String tableName = "detailed_data";
            List<List<String>> result =  EasyExcelUtil.readCsv(excelFilePath).get("data");
            for (List<String> list : result) {
                System.out.println(list);
            }
        }catch (Exception e){
            System.out.println(e);
        }
    }
}
