package com.ada.mapaAstral.service;

import com.ada.mapaAstral.dto.response.MapaAstralCreatedResponse;
import com.ada.mapaAstral.model.MapaAstral;
import com.ada.mapaAstral.model.Pessoa;
import com.ada.mapaAstral.repository.MapaAstralRepository;
import com.ada.mapaAstral.type.ArquivoSalvo;
import com.ada.mapaAstral.type.either.Either;
import com.ada.mapaAstral.type.either.Left;
import com.ada.mapaAstral.type.either.Right;
import com.ada.mapaAstral.type.oneof.*;
import com.ada.mapaAstral.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.*;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.zip.DataFormatException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapaAstralService {

    private final MapaAstralRepository repository;
    private final PessoaService pessoaService;

    public void importar() {

        List<Pessoa> pessoas = pessoaService.buscarPessoas();

        List<MapaAstral> mapasAstrais = criarMapasAstrais(pessoas);

        mapasAstrais.stream().parallel().forEach(this::persistirMapaAstral);
    }

    public OneOf<IllegalArgumentException, DataFormatException, IOException, MapaAstralCreatedResponse> lidaComArquivoEnviado(MultipartFile file) {

        if (!"text/csv".equals(file.getContentType())) {
            return FirstOf.create(new IllegalArgumentException("Arquivo enviado não é um formato válido. Certifique-se de enviar um arquivo csv!"));
        }

        Either<Exception, Pessoa> retornoParsePessoa = parsePessoaFromCSV(file);

        if (retornoParsePessoa.isLeft()) {
            return SecondOf.create(new DataFormatException(
                    "Não foi possivel parsear o arquivo enviado. Certifique-se de enviar os dados da pessoa SEM cabeçalhos na ordem: " +
                            "nome,zoneId nascimento,data e hora nascimento (formato ISO 8601)."));
        }

        Pessoa pessoa = retornoParsePessoa.unsafeGetRight();

        var mapaAstral = montarMapaAstral(pessoa);

        var codigo = gerarCodigoUpload();
        var retornoSalvamento = salvarMapaAstral(mapaAstral, codigo);

        if (retornoSalvamento.isLeft()) {
            return ThirdOf.create(new IOException(retornoSalvamento.unsafeGetLeft().getMessage()));
        }

        return FourthOf.create(new MapaAstralCreatedResponse(codigo, ZonedDateTime.now()));
    }

    private List<MapaAstral> criarMapasAstrais(List<Pessoa> pessoas) {

        return pessoas.stream()
                .parallel()
                .map(this::montarMapaAstral)
                .toList();
    }

    public Either<Exception, Resource> buscarPorNome(String nome) {


        var resultadoBusca = repository.buscarPorNome(nome);

        if (resultadoBusca.isLeft()) {
            return Left.create(new RuntimeException("erro!"));
        }

        Resource resource = new InputStreamResource(resultadoBusca.unsafeGetRight());
        return Right.create(resource);
    }

    private MapaAstral montarMapaAstral(Pessoa pessoa) {

        var signo = getSigno(pessoa.getDataNascimento().toLocalDate());
        var ascendente = getAscendente(signo, pessoa.getDataNascimento().toLocalTime());
        var signoLunar = getSignoLunar(pessoa.getDataNascimento().toLocalTime(), pessoa.getZoneId().getId());

        return new MapaAstral(signo, ascendente, signoLunar, pessoa);
    }

    private String getSigno(LocalDate datanascimento) {
        MonthDay monthDayNascimento = MonthDay.of(datanascimento.getMonth(), datanascimento.getDayOfMonth());

        MonthDay ariesStartDate = MonthDay.of(3, 21);
        MonthDay ariesEndDate = MonthDay.of(4, 20);

        MonthDay sagitarioStartDate = MonthDay.of(11, 22);
        MonthDay sagitarioEndDate = MonthDay.of(12, 21);

        if (Util.isWithinRange(monthDayNascimento, ariesStartDate, ariesEndDate)) {
            return "Aries";
        } else if (Util.isWithinRange(monthDayNascimento, sagitarioStartDate, sagitarioEndDate)) {
            return "Sagitario";
        }

        return "Não tem signo!!";
    }

    private String getAscendente(String signo, LocalTime horarioDeNascimento) {

        if ("Aries".equalsIgnoreCase(signo)) {
            if (Util.isWithinRange(horarioDeNascimento, LocalTime.of(18, 31), LocalTime.of(20, 30))) {
                return "escorpião";
            }
        } else if ("sagitario".equalsIgnoreCase(signo)) {
            if (Util.isWithinRange(horarioDeNascimento, LocalTime.of(10, 31), LocalTime.of(12, 30))) {
                return "Peixes";
            }
        }
        return "Ufa, não tem ascendente";
    }

    private String getSignoLunar(LocalTime time, String localNascimento) {
        Set<String> zones = ZoneId.getAvailableZoneIds();
        for (String s : zones) {
            if (s.contains((localNascimento))) {
                ZoneId zoneID = ZoneId.of(s);

                if (zoneID.toString().equals("America/Recife") && time.isAfter(LocalTime.NOON)) {
                    return "Casimiro";
                }

                if (zoneID.toString().equals("America/Cuiaba") && time.isAfter(LocalTime.NOON)) {
                    return "Odin";
                }
                if (zoneID.toString().equals("America/Sao_Paulo")) {
                    return "Gandalf";
                }
            }
        }
        return "Dinossauro";
    }

    private void persistirMapaAstral(MapaAstral mapaAstral) {
        Either<Exception, ArquivoSalvo> resultadoPersistencia = repository.salvar(mapaAstral);

        if (resultadoPersistencia.isLeft()) {
            System.out.format("Erro ao salvar arquivo csv. Mensagem de exception: %s%n", resultadoPersistencia.unsafeGetLeft().getMessage());
        } else {
            System.out.println(resultadoPersistencia.unsafeGetRight().getMessage());
        }
    }

    private Either<Exception, Pessoa> parsePessoaFromCSV(MultipartFile file) {

        try (Scanner scanner = new Scanner(file.getInputStream())) {

            Pessoa pessoa = Util.createPessoaFromLine(scanner.nextLine());
            return Right.create(pessoa);
        } catch (Exception e) {
            return Left.create(e);
        }
    }

    private String gerarCodigoUpload() {

        return UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    private Either<Exception, ArquivoSalvo> salvarMapaAstral(MapaAstral mapaAstral, String nome) {

        return repository.salvar(mapaAstral, nome);
    }
}
