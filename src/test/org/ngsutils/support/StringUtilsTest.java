package org.ngsutils.support;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void testGetUniqueNames() {
        List<String> names = new ArrayList<String>();
        for (String s: new String[]{"dir/foo.txt.gz", "dir/bar.txt.gz"}) {
            names.add(s);
        }

        List<String> unique = StringUtils.getUniqueNames(names);
        assertEquals("foo", unique.get(0));
        assertEquals("bar", unique.get(1));
    }

}
