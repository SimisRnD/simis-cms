package com.simisinc.platform.domain;

import org.junit.jupiter.api.Test;
import org.meanbean.test.BeanVerifier;
import org.meanbean.util.ClassPathUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

class DomainModelTest {
  @Test
  void testSettersAndGetters() {

    // Verify complex classes separately
    List<String> complexClasses = new ArrayList<>();

    // ProductSku setter enforces changes for BeanUtils usage
    complexClasses.add("com.simisinc.platform.domain.model.ecommerce.ProductSku");

    // Test getters and setters for the domain model
    Class<?>[] beanClasses = ClassPathUtils.findClassesIn("com.simisinc.platform.domain.model");
    for (Class k : beanClasses) {
      String thisClass = k.getName();
      if (complexClasses.contains(thisClass)) {
        continue;
      }
      BeanVerifier.forClass(k)
          .editSettings()
          .registerFactory(Timestamp.class, () -> new Timestamp(System.currentTimeMillis()))
          .edited()
          .verifyGettersAndSetters();
    }
  }
}