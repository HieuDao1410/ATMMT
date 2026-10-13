package vn.edu.atmmt.sessionlab.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.edu.atmmt.sessionlab.notes.NoteService;

@Controller
public class PageController {

    private final NoteService noteService;

    public PageController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/me")
    public String me(Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        return "me";
    }

    @GetMapping("/notes")
    public String notes(Principal principal, Model model) {
        String username = principal.getName();
        model.addAttribute("username", username);
        // Select the owner from the authenticated identity, never a request parameter.
        model.addAttribute("notes", noteService.findByOwner(username));
        return "notes";
    }
}
