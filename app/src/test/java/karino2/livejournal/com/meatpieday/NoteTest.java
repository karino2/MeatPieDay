package karino2.livejournal.com.meatpieday;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import karino2.livejournal.com.meatpieday.json.JsonNote;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class NoteTest {
    @Test
    public void import_isCorrect() throws Exception {
        JsonNote note = readTestNote();

        assertEquals(2, note.cells.size());
    }

    private JsonNote readTestNote() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("test.ipynb");
        return JsonNote.fromJson(in);
    }

    @Test
    public void metadata_updateAt() throws Exception {
        JsonNote note = readTestNote();

        assertEquals(1506077162718L, note.cells.get(0).metadata.updatedAt);
    }

    void verifyStripDate(String expect, String target) {
        assertEquals(expect, Importer.stripDate(target));
    }

    @Test
    public void stripDate_basic()  {
        verifyStripDate("hello.ipynb", "hello.ipynb");
        verifyStripDate("12345678_hello.ipynb", "12345678_hello.ipynb");
        verifyStripDate("hello.ipynb", "20180403_20063245_hello.ipynb");
    }


}