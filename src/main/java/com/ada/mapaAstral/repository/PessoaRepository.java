package com.ada.mapaAstral.repository;

import com.ada.mapaAstral.model.CSVConvertible;
import com.ada.mapaAstral.model.MapaAstral;
import com.ada.mapaAstral.model.Pessoa;
import com.ada.mapaAstral.type.ArquivoSalvo;
import com.ada.mapaAstral.type.either.Either;
import com.ada.mapaAstral.type.either.Left;
import com.ada.mapaAstral.type.either.Right;
import com.ada.mapaAstral.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class PessoaRepository {

    private final static String HOME_DIR = System.getProperty("user.dir");

    public Either<Exception, List<Pessoa>> findAll() {
        try {
            return getPessoasArquivo();
        } catch (Exception e) {
            return Left.create(e);
        }
    }

    private Right<Exception, List<Pessoa>> getPessoasArquivo() throws IOException {
        Path path = Paths.get(HOME_DIR, "input", "pessoas.csv");

        try (Stream<String> fileStream = Files.lines(path, StandardCharsets.UTF_8)) {

            var pessoas = fileStream.parallel()
                    .map(Util::createPessoaFromLine)
                    .toList();

            return Right.create(pessoas);
        }
    }
}


