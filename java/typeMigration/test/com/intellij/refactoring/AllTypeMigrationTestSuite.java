package com.intellij.refactoring;

import com.intellij.codeInsight.ConvertToAtomicIntentionTest;
import com.intellij.codeInsight.ConvertToThreadLocalIntention6Test;
import com.intellij.codeInsight.ConvertToThreadLocalIntentionTest;
import com.intellij.codeInsight.inspections.GuavaInspectionTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  TypeMigrationTest.class,
  TypeMigrationByAtomicRuleTest.class,
  TypeMigrationByThreadLocalRuleTest.class,
  TypeMigrationByLongAdderTest.class,
  MigrateTypeSignatureTest.class,
  ChangeTypeSignatureTest.class,
  WildcardTypeMigrationTest.class,
  ConvertToAtomicIntentionTest.class,
  ConvertToThreadLocalIntentionTest.class,
  ConvertToThreadLocalIntention6Test.class,
  GuavaInspectionTest.class,
})
public class AllTypeMigrationTestSuite {
}