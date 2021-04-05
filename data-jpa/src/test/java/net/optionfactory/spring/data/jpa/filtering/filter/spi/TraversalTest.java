package net.optionfactory.spring.data.jpa.filtering.filter.spi;

import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.TraversalType;
import org.junit.Assert;
import org.junit.Test;

public class TraversalTest {

    @Test
    public void canParseUnqualifiedSingleAttributeTraversal() {
        List<Traversal> parseAll = Traversal.parseAll("a");
        Assert.assertEquals(1, parseAll.size());
        Assert.assertEquals("a", parseAll.get(0).attribute);
        Assert.assertEquals(TraversalType.GET, parseAll.get(0).type);
    }
    @Test
    public void canParseQualifiedSingleAttributeTraversal() {
        List<Traversal> parseAll = Traversal.parseAll("<GET>a");
        Assert.assertEquals(1, parseAll.size());
        Assert.assertEquals("a", parseAll.get(0).attribute);
        Assert.assertEquals(TraversalType.GET, parseAll.get(0).type);
    }
}
