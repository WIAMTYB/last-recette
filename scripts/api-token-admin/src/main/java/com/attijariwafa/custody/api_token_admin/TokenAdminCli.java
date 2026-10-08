package com.attijariwafa.custody.api_token_admin;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public final class TokenAdminCli {
    private static final int DEFAULT_TOKEN_ID_BYTES = 16;
    private static final int DEFAULT_SECRET_BYTES = 32;
    private static final int DEFAULT_BCRYPT_STRENGTH = 12;
    private static final String DEFAULT_TOKEN_ID_PREFIX = "ak_";

    private TokenAdminCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
                printUsage(out);
                return 0;
            }
            if (!"generate".equals(args[0])) {
                err.println("Unknown command: " + args[0]);
                printUsage(err);
                return 2;
            }

            Map<String, String> options = parseOptions(args);
            String tokenIdPrefix = options.getOrDefault("--token-id-prefix", DEFAULT_TOKEN_ID_PREFIX);
            int tokenIdBytes = intOption(options, "--token-id-bytes", DEFAULT_TOKEN_ID_BYTES);
            int secretBytes = intOption(options, "--secret-bytes", DEFAULT_SECRET_BYTES);
            int bcryptStrength = intOption(options, "--bcrypt-strength", DEFAULT_BCRYPT_STRENGTH);
            String clientCode = options.getOrDefault("--client-code", "CHANGE_ME_CLIENT_CODE");
            String createdBy = options.getOrDefault("--created-by", "integration");
            String description = options.get("--description");
            String expiresAt = options.get("--expires-at");

            GeneratedToken token = new SecureTokenGenerator().generate(
                    tokenIdPrefix,
                    tokenIdBytes,
                    secretBytes,
                    bcryptStrength);
            printToken(out, token, clientCode, createdBy, description, expiresAt);
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        } catch (RuntimeException ex) {
            err.println("Unable to generate token material");
            return 1;
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String option = args[i];
            if (!option.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + option);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            options.put(option, args[++i]);
        }
        return options;
    }

    private static int intOption(Map<String, String> options, String name, int defaultValue) {
        String value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printToken(PrintStream out,
                                   GeneratedToken token,
                                   String clientCode,
                                   String createdBy,
                                   String description,
                                   String expiresAt) {
        printTokenTable(out, token);
        out.println();
        out.println("Deliver X-Api-Key as: <Token ID>.<Secret>");
        out.println("Store only the Secret Hash in API_CLIENT_TOKEN.SECRET_HASH.");
        out.println("The clear Secret is printed once and cannot be recovered from the hash.");
        out.println();
        out.println("SQL:");
        out.println("INSERT INTO API_CLIENT_TOKEN (");
        out.println("  TOKEN_ID,");
        out.println("  CLIENT_CODE,");
        out.println("  SECRET_HASH,");
        out.println("  ACTIVE,");
        out.println("  CREATED_BY,");
        out.println("  EXPIRES_AT,");
        out.println("  DESCRIPTION");
        out.println(") VALUES (");
        out.println("  " + sqlString(token.tokenId()) + ",");
        out.println("  " + sqlString(clientCode) + ",");
        out.println("  " + sqlString(token.secretHash()) + ",");
        out.println("  1,");
        out.println("  " + sqlString(createdBy) + ",");
        out.println("  " + sqlTimestamp(expiresAt) + ",");
        out.println("  " + sqlStringOrNull(description));
        out.println(");");
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage:");
        out.println("  api-token-admin generate [--client-code CLIENT_A] [--created-by integration]");
        out.println("                           [--description \"CLIENT_A production token\"]");
        out.println("                           [--expires-at \"2027-05-19 00:00:00\"]");
        out.println("                           [--token-id-prefix ak_] [--token-id-bytes 16]");
        out.println("                           [--secret-bytes 32] [--bcrypt-strength 12]");
    }

    private static String sqlTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return "NULL";
        }
        return "TO_TIMESTAMP(" + sqlString(value) + ", 'YYYY-MM-DD HH24:MI:SS')";
    }

    private static String sqlStringOrNull(String value) {
        if (value == null || value.isBlank()) {
            return "NULL";
        }
        return sqlString(value);
    }

    private static String sqlString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static void printTokenTable(PrintStream out, GeneratedToken token) {
        String[][] rows = {
                {"Token ID", token.tokenId()},
                {"Secret", token.secret()},
                {"Secret Hash", token.secretHash()}
        };
        int labelWidth = 11;
        int valueWidth = maxValueWidth(rows);
        String border = "+" + "-".repeat(labelWidth + 2) + "+" + "-".repeat(valueWidth + 2) + "+";

        out.println(border);
        out.println("| " + padRight("Field", labelWidth) + " | " + padRight("Value", valueWidth) + " |");
        out.println(border);
        for (String[] row : rows) {
            out.println("| " + padRight(row[0], labelWidth) + " | " + padRight(row[1], valueWidth) + " |");
        }
        out.println(border);
    }

    private static int maxValueWidth(String[][] rows) {
        int max = "Value".length();
        for (String[] row : rows) {
            max = Math.max(max, row[1].length());
        }
        return max;
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
