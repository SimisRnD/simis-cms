/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.domain.model;

import com.simisinc.platform.application.CustomFieldListCommand;
import com.simisinc.platform.domain.model.login.UserLogin;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * User of the system
 *
 * @author matt rajkowski
 * @created 4/8/18 4:18 PM
 */
public class User extends Entity {

  private Long id = -1L;
  private String uniqueId = null;

  private String firstName = null;
  private String lastName = null;
  private String title = null;
  private String organization = null;
  private String department = null;
  private String nickname = null;
  private String description = null;
  private String imageUrl = null;
  private String videoUrl = null;
  private String email = null;
  private String username = null;
  private String password = null;

  private String timeZone = null;
  private String city = null;
  private String state = null;
  private String country = null;
  private String postalCode = null;
  private double latitude = 0.0;
  private double longitude = 0.0;

  private boolean enabled = false;
  private String accountToken = null;
  private Timestamp validated = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;

  private List<Role> roleList = null;
  private List<Group> groupList = null;
  private UserLogin lastLogin = null;

  private List<CustomField> customFieldList = null;

  public User() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getFullName() {
    if (StringUtils.isNoneBlank(firstName, lastName)) {
      return firstName + " " + lastName;
    }
    if (StringUtils.isNotBlank(firstName)) {
      return firstName;
    }
    if (StringUtils.isNotBlank(lastName)) {
      return lastName;
    }
    return null;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getOrganization() {
    return organization;
  }

  public void setOrganization(String organization) {
    this.organization = organization;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getVideoUrl() {
    return videoUrl;
  }

  public void setVideoUrl(String videoUrl) {
    this.videoUrl = videoUrl;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public boolean hasGeoPoint() {
    return (latitude != 0 && longitude != 0);
  }

  public boolean isGeocoded() {
    return hasGeoPoint();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getAccountToken() {
    return accountToken;
  }

  public void setAccountToken(String accountToken) {
    this.accountToken = accountToken;
  }

  public Timestamp getValidated() {
    return validated;
  }

  public void setValidated(Timestamp validated) {
    this.validated = validated;
  }

  public boolean isNotValidated() {
    return validated == null;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public List<Role> getRoleList() {
    return roleList;
  }

  public void setRoleList(List<Role> roleList) {
    this.roleList = roleList;
  }

  public boolean hasRole(String code) {
    if (roleList == null) {
      return false;
    }
    for (Role role : roleList) {
      if (role.getCode().equals(code)) {
        return true;
      }
    }
    return false;
  }

  public Role getRole(String code) {
    if (roleList == null) {
      return null;
    }
    for (Role role : roleList) {
      if (role.getCode().equals(code)) {
        return role;
      }
    }
    return null;
  }

  public void removeRole(String code) {
    Role role = getRole(code);
    if (role != null) {
      roleList.remove(role);
    }
  }

  public List<Group> getGroupList() {
    return groupList;
  }

  public void setGroupList(List<Group> groupList) {
    this.groupList = groupList;
  }

  public boolean hasGroup(String groupUniqueId) {
    if (groupList == null) {
      return false;
    }
    for (Group group : groupList) {
      if (group.getUniqueId().equals(groupUniqueId)) {
        return true;
      }
    }
    return false;
  }

  public UserLogin getLastLogin() {
    return lastLogin;
  }

  public void setLastLogin(UserLogin lastLogin) {
    this.lastLogin = lastLogin;
  }

  public List<CustomField> getCustomFieldList() {
    return customFieldList;
  }

  public void setCustomFieldList(List<CustomField> customFieldList) {
    this.customFieldList = customFieldList;
  }

  public void addCustomField(CustomField customField) {
    if (this.getCustomFieldList() == null) {
      this.setCustomFieldList(new ArrayList<>());
    }
    CustomFieldListCommand.addCustomField(this.getCustomFieldList(), customField);
  }
}
