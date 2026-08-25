package com.novaops.backend.asset.mapper;

import com.novaops.backend.asset.model.AssetLogRecord;
import com.novaops.backend.asset.model.AssetRecord;
import com.novaops.backend.asset.dto.RelatedTicket;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetMapper {

  long countAssets(
      @Param("tenantId") String tenantId,
      @Param("status") String status,
      @Param("type") String type,
      @Param("keyword") String keyword
  );

  List<AssetRecord> queryAssets(
      @Param("tenantId") String tenantId,
      @Param("status") String status,
      @Param("type") String type,
      @Param("keyword") String keyword,
      @Param("offset") long offset,
      @Param("limit") long limit
  );

  AssetRecord findAsset(@Param("tenantId") String tenantId, @Param("id") String id);

  void insertAsset(AssetRecord record);

  int updateAsset(AssetRecord record);

  List<AssetRecord> findByIds(@Param("tenantId") String tenantId, @Param("ids") List<String> ids);

  void insertLog(AssetLogRecord record);

  List<AssetLogRecord> listLogs(@Param("assetId") String assetId);

  List<RelatedTicket> listRelatedTickets(@Param("tenantId") String tenantId, @Param("assetId") String assetId);
}
