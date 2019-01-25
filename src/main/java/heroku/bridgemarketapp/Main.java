package heroku.bridgemarketapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.webhook.Payload;
import com.mysema.query.sql.codegen.MetaDataExporter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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

	@RequestMapping("/generate_dsl")
	@ResponseBody
	public String generate() {

		try (Connection connection = this.dataSource.getConnection()) {
			MetaDataExporter exporter = new MetaDataExporter();
			exporter.setPackageName("heroku.bridgemarketapp");
			File metamodel = new File("src/main/java/heroku/bridgemarketapp/model");
			exporter.setTargetFolder(metamodel);
			exporter.export(connection.getMetaData());

			String fileString = "";

			try (BufferedReader br = new BufferedReader(new FileReader(metamodel.getAbsolutePath()))) {
				String line = null;
				while ((line = br.readLine()) != null) {
					fileString += line;
				}
			}

			return fileString;
		} catch (Exception e) {
			return "error" + e.getMessage();
		}
	}

	@RequestMapping("/list")
	@ResponseBody
	public String list(Map<String, Object> model, @RequestParam(value = "text", required = false) String product) {
		try (Connection connection = this.dataSource.getConnection()) {
			Statement stmt = connection.createStatement();

			return this.listToBuy(stmt);
		} catch (Exception e) {
			model.put("message", e.getMessage());
			return "ERROR - " + e.getMessage();
		}
	}

	@RequestMapping("/db")
	String db(Map<String, Object> model) {
		try (Connection connection = this.dataSource.getConnection()) {
			Statement stmt = connection.createStatement();
			stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp);");
			stmt.executeUpdate("INSERT INTO ticks VALUES (now());");
			ResultSet rs = stmt.executeQuery("SELECT tick FROM ticks;");

			ArrayList<String> output = new ArrayList<>();
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
	public String buy(Map<String, Object> model, @RequestParam(value = "co_item", required = false) int id, @RequestParam(value = "text", required = false) String product,
			@RequestParam(value = "user_name", required = false) String userName) {
		try (Connection connection = this.dataSource.getConnection()) {
			Statement stmt = connection.createStatement();
			stmt.executeUpdate("CREATE TABLE IF NOT EXISTS tb_buy (co_item serial primary key, ds_buy varchar, ds_user varchar);");
			stmt.executeUpdate("INSERT INTO tb_buy VALUES ('" + product + "' ,'" + userName.substring(0, 1).toUpperCase() + userName.substring(1) + "');");

			this.sendMessage(this.listToBuy(stmt));

			return "Item adicionado com sucesso!";
		} catch (Exception e) {
			model.put("message", e.getMessage());
			return "ERROR - " + e.getMessage();
		}
	}

	@RequestMapping("/remove")
	@ResponseBody
	public String remove(Map<String, Object> model, @RequestParam(value = "co_item", required = false) int id, @RequestParam(value = "text", required = false) String product,
			@RequestParam(value = "user_name", required = false) String userName) {
		try (Connection connection = this.dataSource.getConnection()) {
			Statement stmt = connection.createStatement();
			stmt.executeUpdate("DELETE FROM tb_buy WHERE tb_buy.co_item = '" + id + "';");

			this.sendMessage("-- " + userName.substring(0, 1).toUpperCase() + userName.substring(1) + " está indo comprar o item: " + id + ". " + product);

			return "Item removido com sucesso!";
		} catch (Exception e) {
			model.put("message", e.getMessage());
			return "ERROR - " + e.getMessage();
		}
	}

	@RequestMapping("/remove_all")
	@ResponseBody
	public void removeAll(Map<String, Object> model, @RequestParam(value = "text", required = false) String product) {
		try (Connection connection = this.dataSource.getConnection()) {
			Statement stmt = connection.createStatement();
			stmt.executeUpdate("DROP TABLE tb_buy;");
		} catch (Exception e) {
			// do nothing
		}
	}

	@RequestMapping("/going")
	@ResponseBody
	public void going(Map<String, Object> model, @RequestParam(value = "user_name", required = false) String userName) {
		try {
			this.sendMessage(userName.substring(0, 1).toUpperCase() + userName.substring(1) + " sinaliza que estará indo no mercado em breve. Façam seus pedidos!");
		} catch (Exception e) {
			// do nothing
		}
	}

	private String listToBuy(Statement stmt) throws Exception {
		ResultSet rs = stmt.executeQuery("SELECT co_item, ds_buy, ds_user FROM tb_buy;");

		String output = "-- ITENS A SEREM COMPRADOS\n";
		while (rs.next()) {
			output += rs.getString("co_item") + ". " + rs.getString("ds_buy") + " - " + rs.getString("ds_user") + "\n";
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
		if (this.dbUrl == null || this.dbUrl.isEmpty()) {
			return new HikariDataSource();
		} else {
			HikariConfig config = new HikariConfig();
			config.setJdbcUrl(this.dbUrl);
			return new HikariDataSource(config);
		}
	}

}