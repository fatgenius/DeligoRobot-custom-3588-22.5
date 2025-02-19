package com.reeman.delige.board;

import com.reeman.delige.board.impl.Board3128;
import com.reeman.delige.board.impl.Board3568;


public class BoardFactory {

    public static Board create(String board) {
        if ("rk312x".equals(board)){
            return new Board3128();
        }else if ("YF3568_XXXE".equals(board)) {
            return new Board3568();
        }  else if ("YF3566_XXXD".equals(board)){
            return new Board3568();
        } else if (board.startsWith("rk3588")) {
            return new Board3128();
        } else {
            throw new RuntimeException("unknown device");
        }
    }
}
