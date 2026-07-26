package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.domain.model.MediaAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * P5.1: Media Asset Repository
 * Handles database queries for media assets
 */
@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

  /**
   * Find media asset by asset_id (UUID)
   */
  Optional<MediaAsset> findByAssetId(String assetId);

  /**
   * Find all non-deleted media assets, paginated
   */
  Page<MediaAsset> findByDeletedAtIsNull(Pageable pageable);

  /**
   * Find media by type (image, pdf) and non-deleted
   */
  Page<MediaAsset> findByAssetTypeAndDeletedAtIsNull(String assetType, Pageable pageable);

  /**
   * Search by name or tags (case-insensitive)
   */
  List<MediaAsset> findByAssetNameContainingIgnoreCase(String name);

  /**
   * Filter by tags
   */
  List<MediaAsset> findByTagsContainingIgnoreCase(String tag);

  /**
   * Count non-deleted media assets
   */
  long countByDeletedAtIsNull();
}
