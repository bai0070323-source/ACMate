package com.itnoduck.acmate.user.controller;
import com.itnoduck.acmate.user.service.UserActivityService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/users/{id}")
public class UserActivityController {
    private final UserActivityService activity;
    public UserActivityController(UserActivityService activity) {this.activity=activity;}
    @GetMapping("/activity") public UserActivityService.Activity activity(@PathVariable long id,@RequestParam(required=false) String platform) {return platform==null?activity.activity(id):activity.activity(id,platform);}
    @GetMapping("/submissions/recent") public List<UserActivityService.Submission> recent(@PathVariable long id,@RequestParam(required=false) String platform) {return platform==null?activity.recent(id):activity.recent(id,platform);}
    @GetMapping("/oj-overview") public UserActivityService.Overview overview(@PathVariable long id,@RequestParam(required=false) String platform) {return activity.overview(id,platform);}
}
