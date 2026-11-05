package org.example.logisticsback.contract;

import java.util.List;

public final class ContractAttachmentViews {
    private ContractAttachmentViews() {}

    public record Metadata(String id, String contractId, String name, String type, String resource,
            String documentDate, String uploader, String uploadedAt, String size, String status,
            String format, String mimeType, boolean demo) {}

    public record Sheet(String name, List<String> columns, List<List<String>> rows, String summary) {}

    public record Preview(String format, boolean demo, List<Sheet> sheets) {}

    public record FileContent(String name, String mimeType, byte[] bytes) {}

    public record ListItem(String id, String contractId, String contractName, String name, String type,
            String resource, String documentDate, String uploader, String uploadedAt, String size,
            String status, String format, String mimeType, boolean demo) {}
}
