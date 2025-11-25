package com.github.squi2rel.nbs.nbs;

import java.util.List;

public record NBSSong(String fileName, double tempo,
                      List<NBSNote> notes, List<NBSLayer> layers, List<NBSInstrument> instruments,
                      long duration) {
}
