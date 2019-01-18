/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import com.github.seratch.jslack.*;
import com.github.seratch.jslack.api.webhook.*;

@Controller
@SpringBootApplication
public class Main {

  @Value("${spring.datasource.url}")
  private String dbUrl;

  @Autowired
  private DataSource dataSource;

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Main.class, args);
  }

  @RequestMapping("/")
  String index() {
    return "index";
  }

  @RequestMapping("/list")
  @ResponseBody
  public String list(Map<String, Object> model, @RequestParam(value="text", required=false) String product) {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();

      return listToBuy(stmt);
    } catch (Exception e) {
      model.put("message", e.getMessage());
      return "ERROR - " + e.getMessage();
    }
  }

  @RequestMapping("/db")
  String db(Map<String, Object> model) {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp);");
      stmt.executeUpdate("INSERT INTO ticks VALUES (now());");
      ResultSet rs = stmt.executeQuery("SELECT tick FROM ticks;");

      ArrayList<String> output = new ArrayList<String>();
      while (rs.next()) {
        output.add("Read from DB: " + rs.getTimestamp("tick"));
      }

      model.put("records", output);
      return "db";
    } catch (Exception e) {
      model.put("message", e.getMessage());
      return "error";
    }
  }

  @RequestMapping("/buy")
  @ResponseBody
  public String buy(Map<String, Object> model, @RequestParam(value="text", required=false) String product, 
    @RequestParam(value="user_name", required=false) String userName) {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS tb_buy (ds_buy varchar, ds_user varchar);");
      stmt.executeUpdate("INSERT INTO tb_buy VALUES ('" + product + "' ,'" + userName + "');");

      sendMessage(listToBuy(stmt));

      return "Item adicionado com sucesso!";
    } catch (Exception e) {
      model.put("message", e.getMessage());
      return "ERROR - " + e.getMessage();
    }
  }

  @RequestMapping("/remove")
  @ResponseBody
  public String remove(Map<String, Object> model, @RequestParam(value="text", required=false) String product, 
    @RequestParam(value="user_name", required=false) String userName) {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("DELETE FROM tb_buy WHERE tb_buy.ds_buy = '"+product+"';");

      sendMessage("-- " + userName + " está indo comprar o item: " + product);

      return "Item removido com sucesso!";
    } catch (Exception e) {
      model.put("message", e.getMessage());
      return "ERROR - " + e.getMessage();
    }
  }

  @RequestMapping("/remove_all")
  @ResponseBody
  public void removeAll(Map<String, Object> model, @RequestParam(value="text", required=false) String product) {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("DROP TABLE tb_buy;");
    } catch (Exception e) {
      // do nothing
    }
  }

  private String listToBuy(Statement stmt) throws Exception {
    ResultSet rs = stmt.executeQuery("SELECT ds_buy, ds_user FROM tb_buy;");

    String output = "-- ITENS A SEREM COMPRADOS\n";
    while (rs.next()) {
      output += rs.getString("ds_buy")+" - "+rs.getString("ds_user")+"\n";
    }
    
    return output;
  }

  private void sendMessage(String value) throws Exception {
    String url = "https://hooks.slack.com/services/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";

    Payload payload = Payload.builder()
      .channel("#mercado")
      .username("Tô indo no mercado")
      .text(value)
      .build();

    Slack slack = Slack.getInstance();
    slack.send(url, payload);
  }

  @Bean
  public DataSource dataSource() throws SQLException {
    if (dbUrl == null || dbUrl.isEmpty()) {
      return new HikariDataSource();
    } else {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      return new HikariDataSource(config);
    }
  }

}