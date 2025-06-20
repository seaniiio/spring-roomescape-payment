package roomescape.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import roomescape.auth.JwtProvider;
import roomescape.booking.reservation.dto.ReservationRequest;
import roomescape.external.tosspayment.TossPaymentClient;
import roomescape.member.Member;
import roomescape.member.MemberRepository;
import roomescape.member.MemberRole;
import roomescape.order.Order;
import roomescape.order.OrderRepository;
import roomescape.reservationtime.ReservationTime;
import roomescape.reservationtime.ReservationTimeRepository;
import roomescape.schedule.Schedule;
import roomescape.schedule.ScheduleRepository;
import roomescape.theme.Theme;
import roomescape.theme.ThemeRepository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Reservation 생성 테스트를 위해 연관 객체인 Member, Theme 등을 저장할 때 api를 이용하지 않고, repository를 이용하도록 한다.
 * </br>
 * 여기서 테스트하고자 하는 것은, http 요청에 의해 내가 작성한 코드들이 잘 실행돼서 db에 반영이 되고, 응답까지 잘 내려주는지 확인
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
public class ReservationIntegrationTest {

    private final int port;

    public ReservationIntegrationTest(@LocalServerPort final int port) {
        this.port = port;
    }

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ReservationTimeRepository reservationTimeRepository;

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @MockBean
    private TossPaymentClient tossPaymentClient;

    @Nested
    class Create {

        @Test
        @DisplayName("/reservations POST 요청이 성공하면 201 CREATED를 응답한다.")
        void create_success() {
            // given
            ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.of(10, 0)));
            Theme theme = themeRepository.save(new Theme("테마명", "테마 설명", "썸네일 URL"));
            Schedule schedule = scheduleRepository.save(new Schedule(LocalDate.now().plusDays(1), reservationTime, theme));
            Member member = memberRepository.save(new Member("may@gmail.com", "1234", "메이", MemberRole.MEMBER));
            Order order = orderRepository.save(new Order("oid_12345", 1000L, member, schedule));

            String token = jwtProvider.provideToken(member.getEmail(), member.getRole(), member.getName());

            // when & then
            RestAssured.given().port(port)
                    .contentType("application/json")
                    .cookie("token", token)
                    .body(new ReservationRequest(schedule.getDate(), theme.getId(), reservationTime.getId(), "pk_12345", order.getId(), order.getAmount(), "NORMAL"))
                    .post("/reservations")
                    .then()
                    .statusCode(201);
        }

        @Test
        @DisplayName("/reservations POST 요청에서, 주문 정보와 결제 정보가 일치하지 않으면 400 BAD_REQUEST를 응답한다.")
        void create_orderAndPaymentInformationNotSame() {
            // given
            ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.of(10, 0)));
            Theme theme = themeRepository.save(new Theme("테마명", "테마 설명", "썸네일 URL"));
            Schedule schedule = scheduleRepository.save(new Schedule(LocalDate.now().plusDays(1), reservationTime, theme));
            Member member = memberRepository.save(new Member("may@gmail.com", "1234", "메이", MemberRole.MEMBER));
            Order order = orderRepository.save(new Order("oid_12345", 1000L, member, schedule));

            String token = jwtProvider.provideToken(member.getEmail(), member.getRole(), member.getName());
            Long wrongAmount = 100000L;

            // when & then
            RestAssured.given().port(port)
                    .contentType("application/json")
                    .cookie("token", token)
                    .body(new ReservationRequest(schedule.getDate(), theme.getId(), reservationTime.getId(), "pk_12345", order.getId(), wrongAmount, "NORMAL"))
                    .post("/reservations")
                    .then()
                    .statusCode(400);
        }
    }

}
