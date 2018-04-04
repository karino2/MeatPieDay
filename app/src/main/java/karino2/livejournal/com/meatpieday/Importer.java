package karino2.livejournal.com.meatpieday;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
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

        long viewOrder = 1;
        ArrayList<Cell> cellList = new ArrayList<>();

        for(JsonCell jcell : note.cells) {
            Cell cell = createCell(book, viewOrder, jcell);
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

    @NonNull
    public Cell createCell(Book book, long viewOrder, JsonCell jcell) {
        Cell cell = new Cell();
        cell.book = book;
        cell.cellType = jcell.getCellTypeValue();
        cell.source = jcell.getCellSource();
        cell.viewOrder = viewOrder;
        cell.lastModified = jcell.metadata.updatedAt;
        return cell;
    }

    static String stripDate(String bookName) {
        // yyyyMMdd_HHmmssSS
        if(Pattern.matches("^[0-9]{8}_[0-9]{8}_.*", bookName)) {
            return bookName.substring("yyyyMMdd_HHmmssSS_".length());
        }
        return bookName;
    }


    public Completable syncReadIpynb(OrmaDatabase orma, Book book, String path) throws IOException {
        File ipynb = new File(path);

        JsonNote note = readIpynb(path);
        return orma.transactionAsCompletable(() -> {
                CellListAdapter.getCellRelation(orma, book)
                        .selector()
                        .executeAsObservable()
                        .subscribe(new Observer<Cell>() {
                            Boolean issueIdMatch = null;
                            int cellIdx = 0;

                            long lastViewOrder = 1;

                            Disposable cancel;

                            @Override
                            public void onSubscribe(Disposable d) {
                                cancel = d;
                            }

                            @Override
                            public void onNext(Cell cell) {
                                // first cell
                                if(issueIdMatch == null) {
                                    if (note.cells.size() == 0) {
                                        issueIdMatch = false;
                                        cancel.dispose();
                                    } else {
                                        JsonCell jcell = note.cells.get(0);
                                        issueIdMatch = issueIdEquals(cell, jcell);
                                        if(issueIdMatch == false)
                                            cancel.dispose();
                                    }
                                }
                                if(issueIdMatch == true) {
                                    if(note.cells.size() > cellIdx) {
                                        JsonCell jcell = note.cells.get(cellIdx);
                                        if (cell.lastModified < jcell.metadata.updatedAt) {
                                            orma.updateCell()
                                                    .idEq(cell.id)
                                                    .cellType(jcell.getCellTypeValue())
                                                    .source(jcell.getCellSource())
                                                    .lastModified(jcell.metadata.updatedAt)
                                                    .execute();
                                        }
                                    }
                                }
                                lastViewOrder = Math.max(lastViewOrder, cell.viewOrder);
                                cellIdx++;
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {
                                // add remaining (if exist).
                                if(issueIdMatch == true && (note.cells.size() > cellIdx)) {
                                    long viewOrder = lastViewOrder+1;
                                    for(int i = cellIdx; i < note.cells.size(); i++) {
                                        JsonCell jcell = note.cells.get(i);
                                        Cell newcell = createCell(book, viewOrder, jcell);
                                        orma.insertIntoCell(newcell);
                                        viewOrder++;
                                    }
                                }
                            }


                        });

        });
    }

    boolean issueIdEquals(Cell cell, JsonCell jcell) {
        if(cell.cellType != Cell.CELL_TYPE_TEXT)
            return false;

        if (jcell.getCellType() != JsonCell.CellType.MARKDOWN)
            return false;

        String jcellid = issueIdIfExist(jcell.getSource());
        String cellid = issueIdIfExist(cell.source);
        if(jcellid == null || cellid == null)
            return false;


        return jcellid.equals(cellid);
    }


    Pattern issueIdPat = Pattern.compile(" *IssueId: *([0-9]+) *");
    public String issueIdIfExist(String source) {
        Matcher matcher = issueIdPat.matcher(source);
        if(!matcher.matches())
            return null;
        return source.substring(matcher.start(1), matcher.end(1));
    }
}
