package vn.edu.atmmt.sessionlab.notes;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class NoteService {

    private final Map<String, List<Note>> notesByOwner = Map.of(
        "alice", List.of(
            new Note("Kế hoạch của Alice", "Ôn tập HTTP, cookie và vòng đời phiên đăng nhập."),
            new Note("Ghi chú riêng của Alice", "Chuẩn bị bài thuyết trình cho nhóm.")),
        "bob", List.of(
            new Note("Kế hoạch của Bob", "Chuẩn bị môi trường thực hành và tài khoản mẫu."),
            new Note("Ghi chú riêng của Bob", "Ghi lại kết quả kiểm tra của ứng dụng."))
    );

    public List<Note> findByOwner(String username) {
        return notesByOwner.getOrDefault(username, List.of());
    }
}
