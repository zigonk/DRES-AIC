import { BrowserModule } from '@angular/platform-browser';
import { APP_INITIALIZER, NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { HttpClientModule } from '@angular/common/http';
import { ServicesModule } from './services/services.module';
import { MatMenuModule } from '@angular/material/menu';
import { CompetitionModule } from './competition/competition.module';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RunModule } from './run/run.module';
import { ViewerModule } from './viewer/viewer.module';
import { AppConfig } from './app.config';
import { UserModule } from './user/user.module';
import { JudgementModule } from './judgement/judgement.module';
import { AccessRoleService } from './services/session/access-role.service';
import { AuditlogModule } from './auditlog/auditlog.module';
import { SharedModule } from './shared/shared.module';
import { CollectionModule } from './collection/collection.module';
import { CompetitionBuilderModule } from './competition/competition-builder/competition-builder.module';

/**
 * Method used to load application config.
 *
 * @param appConfig AppConfig service
 */
export function initializeApp(appConfig: AppConfig) {
  return () => appConfig.load();
}

@NgModule({
  declarations: [AppComponent],
  imports: [
    /* Imported modules. */
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
    MatMenuModule,
    MatTooltipModule,
    HttpClientModule,

    /* Our own modules. */
    SharedModule,
    ServicesModule,
    UserModule,
    AuditlogModule,
    CompetitionModule,
    CompetitionBuilderModule,
    ViewerModule,
    RunModule,
    CollectionModule,
    JudgementModule,
  ],
  providers: [
    AppConfig,
    { provide: APP_INITIALIZER, useFactory: initializeApp, deps: [AppConfig], multi: true },
    AccessRoleService,
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
