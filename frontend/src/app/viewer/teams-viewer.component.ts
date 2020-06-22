import {AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild} from '@angular/core';
import {CompetitionRunService, RunInfo, RunState, ScoreOverview, SubmissionInfo, TaskDescription} from '../../../openapi';
import {combineLatest, Observable, of, Subscription} from 'rxjs';
import {catchError, debounceTime, filter, map, pairwise, retry, shareReplay, switchMap, withLatestFrom} from 'rxjs/operators';
import {AppConfig} from '../app.config';
import {AudioPlayerUtilities} from '../utilities/audio-player.utilities';

@Component({
    selector: 'app-teams-viewer',
    templateUrl: './teams-viewer.component.html',
    styleUrls: ['./teams-viewer.component.scss']
})
export class TeamsViewerComponent implements AfterViewInit, OnDestroy {
    @Input() runId: Observable<number>;
    @Input() info: Observable<RunInfo>;
    @Input() state: Observable<RunState>;
    @Input() taskEnded: Observable<TaskDescription>;

    /** Observable that tracks all the submissions per team. */
    submissions: Observable<SubmissionInfo[][]>;

    /** Observable that tracks the current score per team. */
    scores: Observable<ScoreOverview>;

    /** Reference to the audio file played during countdown. */
    @ViewChild('audio') audio: ElementRef<HTMLAudioElement>;

    submissionSoundEffect: Subscription;
    taskEndedSoundEffect: Subscription;



    constructor(private runService: CompetitionRunService, public config: AppConfig) {}

    ngAfterViewInit(): void {
        /* Observable that tracks all the submissions per team. */
        this.submissions = this.state.pipe(
            switchMap(st => this.runService.getApiRunWithRunidTaskSubmissions(st.id).pipe(
                retry(3),
                catchError((err, o) => {
                    console.log(`[TeamsViewerComponent] Error while loading submissions: ${err?.message}.`);
                    return of(null);
                }),
                filter(sb => sb != null), /* Filter null responses. */
            )),
            withLatestFrom(this.info),
            map(([submissions, info]) => {
                return info.teams.map((v, i) => {
                    return submissions.filter(s => s.team === i);
                });
            }),
            shareReplay({bufferSize: 1, refCount: true}) /* Cache last successful loading of submission. */
        );

        /* Observable that tracks the current score per team. */
        this.scores = this.state.pipe(
            switchMap(st => this.runService.getApiRunScoreWithRunidTask(st.id).pipe(
                retry(3),
                catchError((err, o) => {
                    console.log(`[TeamsViewerComponent] Error while loading scores: ${err?.message}.`);
                    return of(null);
                }),
                filter(sc => sc != null), /* Filter null responses. */
            )),
            shareReplay({bufferSize: 1, refCount: true}) /* Cache last successful loading of score. */
        );

        this.submissionSoundEffect = combineLatest([this.state, this.submissions]).pipe(
            filter(([st, sb]) => st.status === 'RUNNING_TASK'),
            map(([st, sb]) => sb),
            pairwise(),
            debounceTime(500),
            map(([s1, s2]) => {
                const stat1 = [
                    s1.map(s => s.filter(ss => ss.status === 'CORRECT').length).reduce((sum, current) => sum + current, 0),
                    s1.map(s => s.filter(ss => ss.status === 'WRONG').length).reduce((sum, current) => sum + current, 0),
                ];

                const stat2 = [
                    s2.map(s => s.filter(ss => ss.status === 'CORRECT').length).reduce((sum, current) => sum + current, 0),
                    s2.map(s => s.filter(ss => ss.status === 'WRONG').length).reduce((sum, current) => sum + current, 0),
                ];

                return [stat2[0] - stat1[0], stat2[1] - stat1[1]];
            })
        ).subscribe(delta => {
            if (delta[0] > delta[1]) {
                this.audio.nativeElement.src = 'assets/audio/correct.ogg';
                AudioPlayerUtilities.playOnce(this.audio.nativeElement);
            } else if (delta[0] < delta[1]) {
                this.audio.nativeElement.src = 'assets/audio/wrong.ogg';
                AudioPlayerUtilities.playOnce(this.audio.nativeElement);
            }
        });

        /** Subscription for end of task (used to play sound effects). */
        this.taskEndedSoundEffect = this.taskEnded.pipe(
            withLatestFrom(this.submissions),
            map(([task, submission]) => {
                return submission.filter(s => (s.filter(ss => ss.status === 'CORRECT').length) > 0).length > 0;
            })
        ).subscribe(success => {
            if (success) {
                this.audio.nativeElement.src = 'assets/audio/applause.ogg';
            } else {
                this.audio.nativeElement.src = 'assets/audio/sad_trombone.ogg';
            }
            AudioPlayerUtilities.playOnce(this.audio.nativeElement);
        });
    }

    public ngOnDestroy(): void {
        this.submissionSoundEffect.unsubscribe();
        this.submissionSoundEffect = null;

        this.taskEndedSoundEffect.unsubscribe();
        this.taskEndedSoundEffect = null;
    }


    /**
     * Generates a URL for the preview image of a submission.
     *
     * @param submission
     */
    public previewForSubmission(submission: SubmissionInfo): Observable<string> {
        return this.runId.pipe(map(runId => this.config.resolveApiUrl(`/preview/submission/${runId}/${submission.id}`)));
    }

    /**
     *
     * @param team
     */
    public submissionForTeam(team: number): Observable<SubmissionInfo[]> {
        return this.submissions.pipe(
            map(s => {
                if (s != null) {
                    return s[team];
                } else {
                    return [];
                }
            })
        );
    }

    public score(team: number): Observable<string> {
        return this.scores.pipe(
            filter(s => s != null),
            map(scores => scores.scores.find(s => s.teamId === team)?.score.toFixed(0))
        );
    }

    public correctSubmissions(team: number): Observable<number> {
        return this.submissions.pipe(
            map(submissions => submissions[team].filter(s => s.status === 'CORRECT').length)
        );
    }

    public wrongSubmissions(team: number): Observable<number> {
        return this.submissions.pipe(
            map(submissions => submissions[team].filter(s => s.status === 'WRONG').length)
        );
    }

    public indeterminate(team: number): Observable<number> {
        return this.submissions.pipe(
            map(submissions => submissions[team].filter(s => s.status === 'INDETERMINATE').length)
        );
    }
}

