package com.attijariwafa.custody.api_token_admin;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAdminCliTests {

    @Test
    void generatePrintsTokenFieldsWithoutDatabaseInstructions() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = TokenAdminCli.run(
                new String[]{"generate", "--bcrypt-strength", "10"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("| Field       | Value"));
        assertTrue(output.contains("| Token ID    | ak_"));
        assertTrue(output.contains("| Secret      | "));
        assertTrue(output.contains("| Secret Hash | $2"));
        assertTrue(output.contains("X-Api-Key"));
        assertTrue(output.contains("INSERT INTO API_CLIENT_TOKEN ("));
        assertTrue(output.contains("CHANGE_ME_CLIENT_CODE"));
        assertFalse(output.toLowerCase().contains("jdbc"));
    }

    @Test
    void generatePrintsReadableInsertWithEscapedMetadata() {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

        int exitCode = TokenAdminCli.run(
                new String[]{
                        "generate",
                        "--bcrypt-strength", "10",
                        "--client-code", "CLIENT_A",
                        "--created-by", "sec'ops",
                        "--description", "Client A's production token",
                        "--expires-at", "2027-05-19 00:00:00"
                },
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(output.contains("  'CLIENT_A',"));
        assertTrue(output.contains("  'sec''ops',"));
        assertTrue(output.contains("TO_TIMESTAMP('2027-05-19 00:00:00', 'YYYY-MM-DD HH24:MI:SS')"));
        assertTrue(output.contains("  'Client A''s production token'"));
    }
}
