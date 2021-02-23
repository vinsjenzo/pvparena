package net.slipcor.pvparena.exceptions;

import net.slipcor.pvparena.core.Language;

public class GameplayException extends Exception {
    public GameplayException(String message) {
        super(message);
    }

    public GameplayException(String message, Throwable cause) {
        super(message, cause);
    }

    public GameplayException(Language.MSG message) {
        super(Language.parse(message));
    }

    public GameplayException(Language.MSG message, Throwable cause) {
        super(Language.parse(message), cause);
    }
}
