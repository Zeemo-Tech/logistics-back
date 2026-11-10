package org.example.logisticsback.assets;

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
public class EquipmentAttachmentService {
    private static final Set<String> CATEGORIES = Set.of(
            "PHOTO", "CONTRACT", "MANUAL", "INSPECTION_REPORT", "MAINTENANCE_CONTRACT", "QUOTE", "ACCEPTANCE", "OTHER");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final EquipmentAttachmentRepository repository;
    private final AssetService assets;

    public EquipmentAttachmentService(EquipmentAttachmentRepository repository, AssetService assets) {
        this.repository = repository;
        this.assets = assets;
    }

    public List<EquipmentAttachment> list(String assetId) {
        assets.get(assetId);
        return repository.list(assetId);
    }

    @Transactional
    public List<EquipmentAttachment> upload(String assetId, String category, String remark,
                                            List<MultipartFile> files, Long operatorId) {
        assets.get(assetId);
        if (!CATEGORIES.contains(category)) throw new BusinessException(400, "附件类型不合法");
        if (files == null || files.isEmpty()) throw new BusinessException(400, "请选择要上传的附件");
        if (files.size() > 8) throw new BusinessException(400, "单次最多上传 8 个附件");
        for (var file : files) {
            if (file == null || file.isEmpty()) throw new BusinessException(400, "附件不能为空");
            if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException(400, "单个附件不能超过 20MB");
            String name = file.getOriginalFilename() == null ? "未命名附件" : file.getOriginalFilename();
            try {
                repository.insert(UUID.randomUUID().toString(), assetId, category, name,
                        file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                        file.getSize(), Base64.getEncoder().encodeToString(file.getBytes()),
                        operatorId, remark == null ? "" : remark.trim());
            } catch (IOException exception) {
                throw new BusinessException(500, "附件读取失败");
            }
        }
        return repository.list(assetId);
    }

    public EquipmentAttachmentRepository.Stored file(String assetId, String attachmentId) {
        assets.get(assetId);
        var stored = repository.find(attachmentId)
                .orElseThrow(() -> new BusinessException(404, "附件不存在或已删除"));
        if (!stored.metadata().assetId().equals(assetId)) {
            throw new BusinessException(404, "附件不属于当前设备");
        }
        return stored;
    }

    @Transactional
    public void delete(String assetId, String attachmentId) {
        file(assetId, attachmentId);
        repository.delete(attachmentId);
    }
}
