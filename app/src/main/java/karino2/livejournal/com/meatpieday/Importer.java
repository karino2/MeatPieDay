package karino2.livejournal.com.meatpieday;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import karino2.livejournal.com.meatpieday.json.JsonCell;
import karino2.livejournal.com.meatpieday.json.JsonNote;

/**
 * Created by _ on 2018/04/03.
 */

public class Importer {
    public JsonNote readIpynb(String path) throws IOException {
        FileInputStream fis = null;

        try {
            fis =new FileInputStream(path);
            return JsonNote.fromJson(fis);
        } finally {
            if(fis != null)
                fis.close();
        }
    }

    public Completable importIpynb(OrmaDatabase orma, String path) throws IOException {
        File ipynb = new File(path);
        String fname = ipynb.getName();
        if(!fname.endsWith(".ipynb")) {
            throw new IOException("File extention is not ipynb.");
        }
        String bookName= fname.substring(0, fname.length()-6);
        bookName = stripDate(bookName);

        JsonNote note = readIpynb(path);



        Book book = new Book();
        book.name = bookName;
        book.createdTime = new Date(ipynb.lastModified());

        int viewOrder = 1;
        ArrayList<Cell> cellList = new ArrayList<>();

        for(JsonCell jcell : note.cells) {
            Cell cell = new Cell();
            cell.book = book;
            cell.cellType = jcell.getCellType().val;
            if(jcell.getCellType() == JsonCell.CellType.CODE) {
                cell.source = jcell.cellImageAsBase64();
            } else {
                cell.source = jcell.getSource();
            }
            cell.viewOrder = viewOrder;
            cell.lastModified = jcell.metadata.updatedAt;
            cellList.add(cell);

            viewOrder++;
        }

        return orma.transactionAsCompletable(() -> {
            book.id = orma.insertIntoBook(book);
            for(Cell cell : cellList) {
                orma.insertIntoCell(cell);
            }
        });

    }

    static String stripDate(String bookName) {
        // yyyyMMdd_HHmmssSS
        if(Pattern.matches("^[0-9]{8}_[0-9]{8}_.*", bookName)) {
            return bookName.substring("yyyyMMdd_HHmmssSS_".length());
        }
        return bookName;
    }


}
