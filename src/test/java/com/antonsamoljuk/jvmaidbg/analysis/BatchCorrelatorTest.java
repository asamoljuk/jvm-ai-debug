package com.antonsamoljuk.jvmaidbg.analysis;

import com.antonsamoljuk.jvmaidbg.model.IssueCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchCorrelatorTest {

    @Test
    void emptyOrSingleFileProducesNoClusters() {
        assertTrue(BatchCorrelator.correlate(List.of()).isEmpty());
        assertTrue(BatchCorrelator.correlate(List.of(
                new BatchCorrelator.Entry("a.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError")
        )).isEmpty());
    }

    @Test
    void uncorrelatedFilesProduceNoClusters() {
        List<BatchCorrelator.Entry> entries = List.of(
                new BatchCorrelator.Entry("a.log", IssueCategory.NULL_POINTER_EXCEPTION, "NullPointerException"),
                new BatchCorrelator.Entry("b.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("c.log", IssueCategory.SPRING_CONTEXT_FAILURE, "BeanCreationException"));
        assertTrue(BatchCorrelator.correlate(entries).isEmpty());
    }

    @Test
    void groupsBySharedCategoryAndException() {
        List<BatchCorrelator.Entry> entries = List.of(
                new BatchCorrelator.Entry("test1.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("test2.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("test3.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("other.log", IssueCategory.NULL_POINTER_EXCEPTION, "NullPointerException"));
        List<BatchCorrelator.Cluster> clusters = BatchCorrelator.correlate(entries);
        assertEquals(1, clusters.size());
        BatchCorrelator.Cluster c = clusters.get(0);
        assertEquals(IssueCategory.JVM_MEMORY_ERROR, c.category());
        assertEquals("OutOfMemoryError", c.topException());
        assertEquals(3, c.fileCount());
        assertEquals(List.of("test1.log", "test2.log", "test3.log"), c.files());
    }

    @Test
    void differentExceptionsInSameCategoryGroupSeparately() {
        List<BatchCorrelator.Entry> entries = List.of(
                new BatchCorrelator.Entry("a.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("b.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("c.log", IssueCategory.JVM_MEMORY_ERROR, "StackOverflowError"));
        // Only 2 OOMs form a cluster; the lone SOE doesn't
        List<BatchCorrelator.Cluster> clusters = BatchCorrelator.correlate(entries);
        assertEquals(1, clusters.size());
        assertEquals("OutOfMemoryError", clusters.get(0).topException());
        assertEquals(2, clusters.get(0).fileCount());
    }

    @Test
    void clustersSortedByFileCountDescending() {
        List<BatchCorrelator.Entry> entries = List.of(
                new BatchCorrelator.Entry("a.log", IssueCategory.NULL_POINTER_EXCEPTION, "NullPointerException"),
                new BatchCorrelator.Entry("b.log", IssueCategory.NULL_POINTER_EXCEPTION, "NullPointerException"),
                new BatchCorrelator.Entry("c.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("d.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"),
                new BatchCorrelator.Entry("e.log", IssueCategory.JVM_MEMORY_ERROR, "OutOfMemoryError"));
        List<BatchCorrelator.Cluster> clusters = BatchCorrelator.correlate(entries);
        assertEquals(2, clusters.size());
        assertEquals(3, clusters.get(0).fileCount()); // OOM cluster first
        assertEquals(2, clusters.get(1).fileCount()); // NPE cluster second
    }

    @Test
    void nullTopExceptionGroupsTogether() {
        List<BatchCorrelator.Entry> entries = List.of(
                new BatchCorrelator.Entry("a.log", IssueCategory.UNKNOWN, null),
                new BatchCorrelator.Entry("b.log", IssueCategory.UNKNOWN, null));
        List<BatchCorrelator.Cluster> clusters = BatchCorrelator.correlate(entries);
        assertEquals(1, clusters.size());
        assertNull(clusters.get(0).topException());
        assertEquals("UNKNOWN", clusters.get(0).signature());
    }

    @Test
    void signatureFormatIncludesExceptionWhenPresent() {
        BatchCorrelator.Cluster c = new BatchCorrelator.Cluster(
                IssueCategory.HIBERNATE_MAPPING_ERROR, "MappingException", 4, List.of());
        assertEquals("HIBERNATE_MAPPING_ERROR (MappingException)", c.signature());
    }
}
