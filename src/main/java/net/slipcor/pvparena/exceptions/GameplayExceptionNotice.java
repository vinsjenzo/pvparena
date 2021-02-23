package net.slipcor.pvparena.exceptions;

import net.slipcor.pvparena.core.Language;

public class GameplayExceptionNotice extends GameplayException {

    public GameplayExceptionNotice(String message) {
        super(message);
    }

    public GameplayExceptionNotice(String message, Throwable cause) {
        super(message, cause);
    }

    public GameplayExceptionNotice(Language.MSG message) {
        super(message);
    }

    public GameplayExceptionNotice(Language.MSG message, Throwable cause) {
        super(message, cause);
    }
}
