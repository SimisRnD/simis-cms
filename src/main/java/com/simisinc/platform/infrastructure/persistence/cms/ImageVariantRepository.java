/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.infrastructure.persistence.cms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves an image's derived, resized variants (issue #411).
 *
 * @author SimIS Inc.
 */
public class ImageVariantRepository {

  private static Log LOG = LogFactory.getLog(ImageVariantRepository.class);

  private static String TABLE_NAME = "image_variants";
  private static String[] PRIMARY_KEY = new String[] { "image_variant_id" };

  public static List<ImageVariant> findByImageId(long imageId) {
    return (List<ImageVariant>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("image_id = ?", imageId),
        null,
        ImageVariantRepository::buildRecord).getRecords();
  }

  /**
   * Batch form of {@link #findByImageId} for a widget rendering many images on one page (issue
   * #411 PR2) -- one query instead of one-per-row. Mirrors the IN-list pattern already used in
   * {@code ItemRepository}'s category/tag facet filters: one {@code ?} per id, ids bound as real
   * PreparedStatement parameters via {@code SqlUtils}' {@code Long[]} overload, never
   * string-concatenated into the SQL text.
   */
  public static Map<Long, List<ImageVariant>> findByImageIds(Collection<Long> imageIds) {
    Map<Long, List<ImageVariant>> variantsByImageId = new LinkedHashMap<>();
    if (imageIds == null || imageIds.isEmpty()) {
      return variantsByImageId;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < imageIds.size(); i++) {
      if (i > 0) {
        placeholders.append(",");
      }
      placeholders.append("?");
    }
    List<ImageVariant> variants = (List<ImageVariant>) DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("image_id IN (" + placeholders + ")", imageIds.toArray(new Long[0])),
        null,
        ImageVariantRepository::buildRecord).getRecords();
    for (ImageVariant variant : variants) {
      variantsByImageId.computeIfAbsent(variant.getImageId(), k -> new ArrayList<>()).add(variant);
    }
    return variantsByImageId;
  }

  public static ImageVariant findByImageIdAndVariantType(long imageId, String variantType) {
    return (ImageVariant) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("image_id = ?", imageId).add("variant_type = ?", variantType),
        ImageVariantRepository::buildRecord);
  }

  /**
   * Inserts or replaces the given variant. A regenerated variant (same imageId + variantType)
   * replaces the prior row rather than accumulating duplicates -- {@code image_variants} has a
   * unique index on (image_id, variant_type) that would otherwise reject the insert.
   */
  public static ImageVariant save(ImageVariant record) {
    ImageVariant existing = findByImageIdAndVariantType(record.getImageId(), record.getVariantType());
    if (existing != null) {
      record.setId(existing.getId());
      return update(record);
    }
    return add(record);
  }

  private static ImageVariant add(ImageVariant record) {
    SqlUtils insertValues = new SqlUtils()
        .add("image_id", record.getImageId())
        .add("variant_type", record.getVariantType())
        .add("path", record.getFileServerPath())
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("width", record.getWidth())
        .add("height", record.getHeight());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static ImageVariant update(ImageVariant record) {
    // A regenerated variant must report a fresh `modified` -- StreamImageWidget uses it as the
    // Last-Modified/If-Modified-Since freshness signal, and `created` only reflects the first
    // time this (imageId, variantType) row was ever written.
    SqlUtils updateValues = new SqlUtils()
        .add("path", record.getFileServerPath())
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("width", record.getWidth())
        .add("height", record.getHeight())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils().add("image_variant_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static ImageVariant buildRecord(ResultSet rs) {
    try {
      ImageVariant record = new ImageVariant();
      record.setId(rs.getLong("image_variant_id"));
      record.setImageId(rs.getLong("image_id"));
      record.setVariantType(rs.getString("variant_type"));
      record.setFileServerPath(rs.getString("path"));
      record.setFileLength(rs.getLong("file_length"));
      record.setFileType(rs.getString("file_type"));
      record.setWidth(rs.getInt("width"));
      record.setHeight(rs.getInt("height"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
