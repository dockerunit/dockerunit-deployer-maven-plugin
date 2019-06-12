package com.github.dockerunit.deployer.commands;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.table.*;

import java.util.Random;

@ShellComponent
public class ListCommands {

    private static final String TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt " +
            "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
            "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in " +
            "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat " +
            "non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";


    @ShellMethod(value = "Lists the currently running services", key = {"get-svc"})
    public Table listServices(){
        String[][] data = new String[3][3];
        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);

        Random r = new Random();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                data[i][j] = TEXT.substring(0, TEXT.length() / 2 + r.nextInt(TEXT.length() / 2));
                tableBuilder.on(at(i, j)).addAligner(SimpleHorizontalAligner.values()[j]);
                tableBuilder.on(at(i, j)).addAligner(SimpleVerticalAligner.values()[i]);
            }
        }

        return tableBuilder.addFullBorder(BorderStyle.fancy_light).build();
    }

    public static CellMatcher at(final int theRow, final int col) {
        return new CellMatcher() {
            @Override
            public boolean matches(int row, int column, TableModel model) {
                return row == theRow && column == col;
            }
        };
    }

}
