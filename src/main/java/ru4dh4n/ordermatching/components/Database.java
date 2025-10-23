package ru4dh4n.ordermatching.components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.CRC32;

@Component
public class Database {

    private static final String DB_RESOURCE_PATH = "db/database.sql";
    private final String databaseLocation;

    public String getDatabaseLocation() { return this.databaseLocation; }
    private static String getResourceHash() throws IOException, NoSuchAlgorithmException {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(Database.DB_RESOURCE_PATH)) {
            if (in == null) throw new IOException("couldn't find file for hashing: " + Database.DB_RESOURCE_PATH);

            // this doesn't need security, just checking whether its changed
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                crc.update(buffer, 0, bytesRead);
            }

            return Long.toHexString(crc.getValue());
        }
    }

    public Database(@Value("${database.location}") String fileLocation) {
        this.databaseLocation = fileLocation;

        File file = new File(fileLocation);
        File hashFile = new File(fileLocation + ".hash");

        try {
            String currentSchemaHash = getResourceHash();

            if (file.exists()) {
                if (!file.isFile()) throw new IllegalStateException(String.format("%s is not a file", fileLocation));
                if (!file.canRead()) throw new IllegalStateException(String.format("%s is not readable", fileLocation));
                if (!file.canWrite()) throw new IllegalStateException(String.format("%s is not writable", fileLocation));

                String storedHash = "";
                if (hashFile.exists() && hashFile.isFile() && hashFile.canRead()) {
                    storedHash = Files.readString(hashFile.toPath());
                }

                if (!storedHash.equals(currentSchemaHash)) {
                    throw new IllegalStateException("DANGER: Database schema has changed, but existing financial data was found.");
                }

                return;
            }

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new IllegalStateException("could not create directory:" + parentDir.getAbsolutePath());
                }
            }

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(DB_RESOURCE_PATH)) {
                if (inputStream == null) {
                    throw new IllegalStateException("database resource not found:" + DB_RESOURCE_PATH);
                }

                String sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                SQLiteDataSource dataSource = new SQLiteDataSource();
                dataSource.setUrl("jdbc:sqlite:" + fileLocation);

                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {

                    String[] statements = sql.split(";");
                    for (String statement : statements) {
                        String trimmed = statement.trim();
                        if (!trimmed.isEmpty()) {
                            stmt.execute(trimmed);
                        }
                    }
                }

                Files.writeString(hashFile.toPath(), currentSchemaHash, StandardCharsets.UTF_8);
            }
        } catch (IOException | NoSuchAlgorithmException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}