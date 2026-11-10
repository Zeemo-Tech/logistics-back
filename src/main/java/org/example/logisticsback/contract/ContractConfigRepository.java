package org.example.logisticsback.contract;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractConfigRepository {
    private final JdbcTemplate jdbc;

    public ContractConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractConfigModels.Category> listCategories(boolean activeOnly) {
        var sql = "select * from contract_categories" + (activeOnly ? " where status = 'ACTIVE'" : "")
                + " order by sort_order, name";
        return jdbc.query(sql, this::mapCategory);
    }

    public Optional<ContractConfigModels.Category> findCategory(String id) {
        return jdbc.query("select * from contract_categories where id = ?", this::mapCategory, id)
                .stream().findFirst();
    }

    public void insertCategory(String id, String name, String status, int sortOrder) {
        jdbc.update("insert into contract_categories (id, name, status, sort_order) values (?, ?, ?, ?)",
                id, name, status, sortOrder);
    }

    public void updateCategory(String id, String name, String status, int sortOrder) {
        jdbc.update("update contract_categories set name = ?, status = ?, sort_order = ?, updated_at = current_timestamp where id = ?",
                name, status, sortOrder, id);
    }

    public void renameCategory(String oldName, String newName) {
        jdbc.update("update contracts set category = ? where category = ?", newName, oldName);
        jdbc.update("update contract_templates set category = ? where category = ?", newName, oldName);
    }

    public void deleteCategory(String id) {
        jdbc.update("delete from contract_categories where id = ?", id);
    }

    public int countContractsByCategory(String name) {
        return count("select count(*) from contracts where category = ?", name);
    }

    public int countTemplatesByCategory(String name) {
        return count("select count(*) from contract_templates where category = ?", name);
    }

    public List<ContractConfigModels.Template> listTemplates(boolean activeOnly) {
        var sql = "select * from contract_templates" + (activeOnly ? " where status = 'ACTIVE'" : "")
                + " order by sort_order, title";
        return jdbc.query(sql, this::mapTemplate);
    }

    public Optional<ContractConfigModels.Template> findTemplate(String id) {
        return jdbc.query("select * from contract_templates where id = ?", this::mapTemplate, id)
                .stream().findFirst();
    }

    public void insertTemplate(String id, ContractConfigModels.TemplateRequest input, String status, int sortOrder) {
        jdbc.update("""
                insert into contract_templates (id, title, resource_type, category, subject, purpose, content, status, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, input.title().trim(), text(input.resourceType()), text(input.category()),
                text(input.subject()), text(input.purpose()), text(input.content()), status, sortOrder);
    }

    public void updateTemplate(String id, ContractConfigModels.TemplateRequest input, String status, int sortOrder) {
        jdbc.update("""
                update contract_templates set title = ?, resource_type = ?, category = ?, subject = ?,
                    purpose = ?, content = ?, status = ?, sort_order = ?, updated_at = current_timestamp where id = ?
                """, input.title().trim(), text(input.resourceType()), text(input.category()),
                text(input.subject()), text(input.purpose()), text(input.content()), status, sortOrder, id);
    }

    public void deleteTemplate(String id) {
        jdbc.update("delete from contract_templates where id = ?", id);
    }

    public int countContractsByTemplate(String id) {
        return count("select count(*) from contracts where template_id = ?", id);
    }

    public List<ContractConfigModels.ResourceType> listResourceTypes(boolean activeOnly) {
        var sql = "select * from park_resource_types" + (activeOnly ? " where status = 'ACTIVE'" : "")
                + " order by sort_order, name";
        return jdbc.query(sql, this::mapResourceType);
    }

    public Optional<ContractConfigModels.ResourceType> findResourceType(String id) {
        return jdbc.query("select * from park_resource_types where id = ?", this::mapResourceType, id)
                .stream().findFirst();
    }

    public void insertResourceType(String id, String code, String name, String status, int sortOrder) {
        jdbc.update("insert into park_resource_types (id, code, name, status, sort_order) values (?, ?, ?, ?, ?)",
                id, code, name, status, sortOrder);
    }

    public void updateResourceType(String id, String code, String name, String status, int sortOrder) {
        jdbc.update("update park_resource_types set code = ?, name = ?, status = ?, sort_order = ?, updated_at = current_timestamp where id = ?",
                code, name, status, sortOrder, id);
    }

    public void renameResourceType(String oldName, String newName) {
        jdbc.update("update park_resources set resource_type = ? where resource_type = ?", newName, oldName);
        jdbc.update("update contract_templates set resource_type = ? where resource_type = ?", newName, oldName);
        jdbc.update("update contracts set resource_type = ? where resource_type = ?", newName, oldName);
    }

    public void deleteResourceType(String id) {
        jdbc.update("delete from park_resource_types where id = ?", id);
    }

    public int countResourcesByType(String name) {
        return count("select count(*) from park_resources where resource_type = ?", name);
    }

    public int countTemplatesByResourceType(String name) {
        return count("select count(*) from contract_templates where resource_type = ?", name);
    }

    public List<ContractConfigModels.Resource> listResources(boolean activeOnly) {
        var sql = "select * from park_resources" + (activeOnly ? " where status = 'ACTIVE'" : "")
                + " order by sort_order, name";
        return jdbc.query(sql, this::mapResource);
    }

    public Optional<ContractConfigModels.Resource> findResource(String id) {
        return jdbc.query("select * from park_resources where id = ?", this::mapResource, id)
                .stream().findFirst();
    }

    public void insertResource(String id, String code, ContractConfigModels.ResourceRequest input, String status, int sortOrder) {
        jdbc.update("""
                insert into park_resources (id, code, name, resource_type, address, note, status, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, code, input.name().trim(), input.resourceType().trim(), text(input.address()),
                text(input.note()), status, sortOrder);
    }

    public void updateResource(String id, String code, ContractConfigModels.ResourceRequest input, String status, int sortOrder) {
        jdbc.update("""
                update park_resources set code = ?, name = ?, resource_type = ?, address = ?, note = ?,
                    status = ?, sort_order = ?, updated_at = current_timestamp where id = ?
                """, code, input.name().trim(), input.resourceType().trim(), text(input.address()),
                text(input.note()), status, sortOrder, id);
    }

    public void renameResource(String id, String newName) {
        jdbc.update("update contracts set resource = ? where resource_id = ?", newName, id);
    }

    public void deleteResource(String id) {
        jdbc.update("delete from park_resources where id = ?", id);
    }

    public int countContractsByResource(String id) {
        return count("select count(*) from contracts where resource_id = ?", id);
    }

    public boolean categoryNameExists(String name, String excludeId) {
        return exists("select count(*) from contract_categories where name = ?", name, excludeId);
    }

    public boolean templateTitleExists(String title, String excludeId) {
        return exists("select count(*) from contract_templates where title = ?", title, excludeId);
    }

    public boolean resourceTypeNameExists(String name, String excludeId) {
        return exists("select count(*) from park_resource_types where name = ?", name, excludeId);
    }

    public boolean resourceTypeCodeExists(String code, String excludeId) {
        return exists("select count(*) from park_resource_types where code = ?", code, excludeId);
    }

    public boolean resourceNameExists(String name, String excludeId) {
        return exists("select count(*) from park_resources where name = ?", name, excludeId);
    }

    public boolean resourceCodeExists(String code, String excludeId) {
        return exists("select count(*) from park_resources where code = ?", code, excludeId);
    }

    private boolean exists(String sql, String value, String excludeId) {
        if (excludeId == null || excludeId.isBlank()) {
            return count(sql, value) > 0;
        }
        return count(sql + " and id <> ?", value, excludeId) > 0;
    }

    private int count(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ContractConfigModels.Category mapCategory(ResultSet result, int rowNumber) throws SQLException {
        return new ContractConfigModels.Category(
                result.getString("id"), result.getString("name"), result.getString("status"),
                result.getInt("sort_order"));
    }

    private ContractConfigModels.Template mapTemplate(ResultSet result, int rowNumber) throws SQLException {
        return new ContractConfigModels.Template(
                result.getString("id"), result.getString("title"), result.getString("resource_type"),
                result.getString("category"), result.getString("subject"), result.getString("purpose"),
                result.getString("content"), result.getString("status"), result.getInt("sort_order"));
    }

    private ContractConfigModels.ResourceType mapResourceType(ResultSet result, int rowNumber) throws SQLException {
        return new ContractConfigModels.ResourceType(
                result.getString("id"), result.getString("code"), result.getString("name"),
                result.getString("status"), result.getInt("sort_order"));
    }

    private ContractConfigModels.Resource mapResource(ResultSet result, int rowNumber) throws SQLException {
        return new ContractConfigModels.Resource(
                result.getString("id"), result.getString("code"), result.getString("name"),
                result.getString("resource_type"), result.getString("address"), result.getString("note"),
                result.getString("status"), result.getInt("sort_order"));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
