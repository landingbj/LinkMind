package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.database.pojo.TableColumnInfo;
import ai.dto.SqlToTextRequest;
import ai.dto.TextToSqlRequest;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.BaseServlet;
import ai.servlet.annotation.Post;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlApiServlet extends BaseServlet {
     private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("text2sql")) {
            this.textToSql(req, resp);
        }else if (method.equals("sql2text")) {
            this.sqlToText(req, resp);
        }

    }
        @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

     @Post("textToSql")
    public void textToSql(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        String jsonString = IOUtils.toString(req.getInputStream(),
                StandardCharsets.UTF_8);
        TextToSqlRequest qaRequest = gson.fromJson(jsonString, TextToSqlRequest.class);

        String demand = qaRequest.getDemand();
        String out = toSql(demand,qaRequest.getTables(),qaRequest.getDatabaseName(),qaRequest.getStorage());
        //   System.out.println("out1的回答是："+out);
        String outcome =  extractContentWithinBraces(out);
        qaRequest.setText(out);
        qaRequest.setSql(outcome);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        result.put("data", qaRequest);
        responsePrint(resp, toJson(result));
    }

    @Post("sqlToText")
    public void sqlToText(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        Map<String, Object> result = new HashMap<String, Object>();
        SqlToTextRequest qaRequest = gson.fromJson(jsonString, SqlToTextRequest.class);

        String out = qaRequest.getSql();

        String outcome =  "";
       List<Map<String, Object>> list = new ArrayList<>();
         Gson gson = new Gson();
            if (out != null){
                outcome =  extractContentWithinBraces(out);
                 //System.out.println("outcome:" + outcome);
                String sql = extractContentWithinBraces(outcome);
                System.out.println("sql:" + sql);
                try {
                    list = MysqlAdapter.getInstance(qaRequest.getDatabaseName(),qaRequest.getStorage()).sqlToValue(sql);
                }catch (Exception e){
                    list = new ArrayList<>();
                }

                String msg = toText(qaRequest.getDemand(),gson.toJson(list),qaRequest.getTables(),qaRequest.getDatabaseName(),qaRequest.getStorage());

                result.put("status", "success");
                result.put("data", msg);
                result.put("list",list);
            }else {
             result.put("status", "failed");
             result.put("data", "The SQL is null");
            }
        responsePrint(resp, toJson(result));

    }



      public String toSql(String demand,String tableNeam,String databaseName,String storageName) {
        MysqlAdapter mysqlAdapter= MysqlAdapter.getInstance(databaseName, storageName);
        String[] tableNames = tableNeam.split("[,，]");
        String tableParsing = tableNames[0].isEmpty() ? "tableNeam" : tableNames[0];
        List<TableColumnInfo> list = mysqlAdapter.getTableColumnInfo(tableNeam);
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        chatCompletionRequest.setCategory("default");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("现在你是一个数据分析师,MYSQL大神,请根据用户提供的表的信息，以及用户的需求，写出效率最高的SQL," +
                "当涉及到中文字段的时候你比较喜欢使用模糊查询." +
                "表信息如下："+
                tableParsing(list)+
                "输并且要求输出的S0L以#开头,以#结尾，样例如下:" +
                "#SELECT * FROM "+tableParsing+" WHERE city LIKE '%北京%' AND name LIKE '%全%';# 或" +
                "#SELECT COUNT(*) FROM "+tableParsing+";#" +
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
        return out;
    }

    public String tableParsing(List<TableColumnInfo> list) {
       StringBuilder tableresult = new StringBuilder();
       Map<String, List<TableColumnInfo>> groupedByTable = list.stream()
         .collect(Collectors.groupingBy(TableColumnInfo::getTableName));
        for (Map.Entry<String, List<TableColumnInfo>> tableEntry : groupedByTable.entrySet()) {
            String tableKey = tableEntry.getKey();
            List<TableColumnInfo> columns = tableEntry.getValue();
            tableresult.append("表名为:").append(tableKey).append(";\n");
            for (TableColumnInfo column : columns) {
                tableresult.append(String.format("字段: %s %s",
                        column.getColumnName(),
                        column.getColumnType()));

                if (!column.getColumnRemark().isEmpty()) {
                    tableresult.append(" 备注为: ").append(column.getColumnRemark());
                }
                tableresult.append("；\n");
            }
        }
        return tableresult.toString();
    }

     public String toText(String demand,String outMsg,String tableNeam,String databaseName,String name) {
         MysqlAdapter mysqlAdapter= MysqlAdapter.getInstance(databaseName,name);
         List<TableColumnInfo> list = mysqlAdapter.getTableColumnInfo(tableNeam);
        //mock request
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.8);
        chatCompletionRequest.setMax_tokens(1024);
        chatCompletionRequest.setCategory("default");
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("现在你是一个数据分析师,请根据用户提供的表的信息，用户的需求，以及你查询数据库返回的信息，给客户一个满意的回答." +
                "表信息如下："+
                tableParsing(list)+
                "用户需求:“" + demand+"”。" +
                "你结合需求查数据库返回的信息的信息是：“"+outMsg+
                "”注意不需要分析过程。");
        chatCompletionRequest.setMessages(Lists.newArrayList(message));
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setModel(mysqlAdapter.model);
        CompletionsService completionsService = new CompletionsService();
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        String out = null;
        if(result != null){
            out = result.getChoices().get(0).getMessage().getContent();
        }
        return out;
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
        //String res = input.substring(0, firstHashIndex + 1) + betweenHashes + input.substring(lastHashIndex);
        //res.replace("#", "");
        return betweenHashes.replace("#", "");
    }

    @Test
    public void mysql() {
        List<Map<String, Object>> list = MysqlAdapter.getInstance().sqlToValue("SELECT * FROM table_info;");
        Gson gson = new Gson();

        for (Object o : list) {
            System.out.println(gson.toJson(o));
        }
    }
}
