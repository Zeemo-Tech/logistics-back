package org.example.logisticsback.maintenance;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkOrderAttachmentService {
    private static final Set<String> CATEGORIES = Set.of(
            "PHOTO", "VIDEO", "QUOTE", "REPAIR_PLAN", "ACCEPTANCE", "OTHER");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private final WorkOrderAttachmentRepository repository;
    private final WorkOrderService orders;

    public WorkOrderAttachmentService(WorkOrderAttachmentRepository repository, WorkOrderService orders) {
        this.repository = repository;
        this.orders = orders;
    }

    public List<WorkOrderAttachment> list(String workOrderId) {
        orders.get(workOrderId);
        return repository.list(workOrderId);
    }

    @Transactional
    public List<WorkOrderAttachment> upload(String workOrderId, String category, String remark,
                                            List<MultipartFile> files, Long operatorId) {
        orders.get(workOrderId);
        if (!CATEGORIES.contains(category)) throw new BusinessException(400, "附件类型不合法");
        if (files == null || files.isEmpty()) throw new BusinessException(400, "请选择要上传的附件");
        if (files.size() > 8) throw new BusinessException(400, "单次最多上传 8 个附件");
        for (var file : files) {
            if (file == null || file.isEmpty()) throw new BusinessException(400, "附件不能为空");
            if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(400, "单个附件不能超过 50MB");
            String name = file.getOriginalFilename() == null ? "未命名附件" : file.getOriginalFilename();
            try {
                repository.insert(UUID.randomUUID().toString(), workOrderId, category, name,
                        file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                        file.getSize(), Base64.getEncoder().encodeToString(file.getBytes()),
                        operatorId, remark == null ? "" : remark.trim());
            } catch (IOException exception) {
                throw new BusinessException(500, "附件读取失败");
            }
        }
        return repository.list(workOrderId);
    }

    public WorkOrderAttachmentRepository.Stored file(String workOrderId, String attachmentId) {
        orders.get(workOrderId);
        var stored = repository.find(attachmentId)
                .orElseThrow(() -> new BusinessException(404, "附件不存在或已删除"));
        if (!stored.metadata().workOrderId().equals(workOrderId)) {
            throw new BusinessException(404, "附件不属于当前工单");
        }
        return stored;
    }

    @Transactional
    public void delete(String workOrderId, String attachmentId) {
        file(workOrderId, attachmentId);
        repository.delete(attachmentId);
    }
}
